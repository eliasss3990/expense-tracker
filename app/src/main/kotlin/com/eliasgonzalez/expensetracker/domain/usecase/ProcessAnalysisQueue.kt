package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisEvent
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisMode
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisModeProvider
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisEventKey
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisQueueRepository
import com.eliasgonzalez.expensetracker.domain.analysis.CandidateAnnouncer
import com.eliasgonzalez.expensetracker.domain.analysis.DecideRegistration
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionAudit
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionAuditRepository
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionEffect
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationPass
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationRequest
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationResult
import com.eliasgonzalez.expensetracker.domain.analysis.ParserEvidence
import com.eliasgonzalez.expensetracker.domain.analysis.QueuedAnalysis
import com.eliasgonzalez.expensetracker.domain.analysis.RegistrationDecision
import com.eliasgonzalez.expensetracker.domain.analysis.RegistrationOutcome
import com.eliasgonzalez.expensetracker.domain.analysis.RegistrationPolicy
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionInterpreter
import com.eliasgonzalez.expensetracker.domain.log.AppLog
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "AiDecision"

/**
 * Procesa la cola en serie: interpreta (con el modelo detras de [TransactionInterpreter]),
 * decide ([DecideRegistration]), aplica el efecto segun el modo, y deja SIEMPRE una auditoria
 * persistida y una linea de log con el por que (codigos, ids, tiempos; nunca importes,
 * comercios ni texto de la notificacion).
 *
 * Nunca se pierde un aviso: si el modelo falla tras los reintentos y hay parser, sale el
 * candidato de siempre. Un resultado no terminal (la base aun no cargo) se reintenta sin
 * contar como intento.
 */
class ProcessAnalysisQueue(
    private val queue: AnalysisQueueRepository,
    private val interpreter: TransactionInterpreter,
    private val decide: DecideRegistration,
    private val createCandidate: CreateCandidate,
    private val findPendingCandidate: FindPendingCandidate,
    private val autoRegister: AutoRegisterExpense,
    private val announcer: CandidateAnnouncer,
    private val audit: DecisionAuditRepository,
    private val modeProvider: AnalysisModeProvider,
    private val requireCrossCheck: Boolean = true,
    private val maxAttempts: Int = 3,
    private val retentionMillis: Long = 30L * 24 * 60 * 60 * 1000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val drainMutex = Mutex()

    suspend fun recoverOnStartup() {
        queue.recoverProcessing()
        val cutoff = now() - retentionMillis
        queue.pruneBefore(cutoff)
        audit.pruneBefore(cutoff)
    }

    suspend fun drain() = drainMutex.withLock {
        val deferred = mutableSetOf<Long>()
        while (true) {
            val item = queue.nextPending(deferred, maxAttempts) ?: break
            if (!queue.markProcessing(item.id)) continue
            if (!process(item)) deferred += item.id
        }
    }

    /** true si el item quedo terminado; false si se reintentara mas tarde. */
    private suspend fun process(item: QueuedAnalysis): Boolean {
        val event = item.event
        val mode = modeProvider.currentMode()
        val results = try {
            interpret(event)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return handleInterpreterFailure(item, mode, error)
        }

        val decision = decide(event.notification, event.parser, results, RegistrationPolicy(event.trust, requireCrossCheck))
        val effect = try {
            apply(mode, event, decision)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            queue.markRetry(item.id, error.javaClass.simpleName)
            AppLog.w(TAG, "evento=${AnalysisEventKey.short(event.key)} efecto fallido, se reintenta: ${error.javaClass.simpleName}")
            return false
        }
        if (effect == null) {
            queue.markRetry(item.id, "NotReady", refundAttempt = true)
            AppLog.w(TAG, "evento=${AnalysisEventKey.short(event.key)} base sin cargar, se reintenta")
            return false
        }
        record(event, mode, decision, effect, results, null)
        queue.markDone(item.id)
        return true
    }

    private suspend fun interpret(event: AnalysisEvent): List<InterpretationResult> {
        val primary = interpreter.interpret(InterpretationRequest(event.notification, event.parser, InterpretationPass.PRIMARY))
        if (!requireCrossCheck) return listOf(primary)
        val cross = interpreter.interpret(InterpretationRequest(event.notification, event.parser, InterpretationPass.CROSS_CHECK))
        return listOf(primary, cross)
    }

    private suspend fun handleInterpreterFailure(item: QueuedAnalysis, mode: AnalysisMode, error: Exception): Boolean {
        val event = item.event
        val errorClass = error.javaClass.simpleName
        // item.attempts es el valor ANTES de markProcessing: este fue el intento numero attempts + 1.
        val attemptNumber = item.attempts + 1
        if (attemptNumber < maxAttempts) {
            queue.markRetry(item.id, errorClass)
            AppLog.w(TAG, "evento=${AnalysisEventKey.short(event.key)} el modelo fallo (intento $attemptNumber/$maxAttempts): $errorClass")
            return false
        }
        // Agotados los reintentos: nunca perder el aviso. Con parser, sale el candidato de siempre
        // (salvo en Sombra, donde el flujo de siempre ya lo creo al llegar).
        val parser = (event.parser as? ParserEvidence.Parsed)?.snapshot
        val effect = when {
            mode == AnalysisMode.SHADOW -> AppliedEffect(DecisionEffect.SHADOW_ONLY)
            parser == null -> AppliedEffect(DecisionEffect.GAVE_UP)
            else -> {
                val candidate = candidateFrom(
                    event, parser.amount, parser.currency, parser.merchant, parser.merchantConfident, parser.parserId,
                )
                val id = createAndAnnounce(candidate)
                AppliedEffect(if (id != null) DecisionEffect.FALLBACK_LEGACY else DecisionEffect.DUPLICATE, candidateId = id)
            }
        }
        record(event, mode, null, effect, emptyList(), errorClass)
        queue.markGaveUp(item.id, errorClass)
        return true
    }

    /** null = no terminal (hay que reintentar). */
    private suspend fun apply(mode: AnalysisMode, event: AnalysisEvent, decision: RegistrationDecision): AppliedEffect? {
        if (mode == AnalysisMode.SHADOW) return AppliedEffect(DecisionEffect.SHADOW_ONLY)
        return when (decision.outcome) {
            RegistrationOutcome.DISCARD -> AppliedEffect(DecisionEffect.DISCARDED)
            RegistrationOutcome.AUTO_REGISTER ->
                if (mode == AnalysisMode.AUTOMATIC) autoRegisterEffect(event, decision) else candidateEffect(event, decision)
            RegistrationOutcome.REVIEW -> candidateEffect(event, decision)
        }
    }

    private suspend fun autoRegisterEffect(event: AnalysisEvent, decision: RegistrationDecision): AppliedEffect? {
        val result = autoRegister(
            eventKey = event.key,
            amount = decision.amount!!,
            currency = decision.currency!!,
            merchant = decision.merchant!!,
            merchantConfident = decision.merchantConfident,
            occurredAt = event.notification.timestamp,
            sourceApp = event.notification.applicationName,
        )
        return when (result) {
            is AutoRegisterResult.Registered -> AppliedEffect(DecisionEffect.AUTO_REGISTERED, expenseId = result.expenseId)
            AutoRegisterResult.Duplicate -> AppliedEffect(DecisionEffect.DUPLICATE)
            AutoRegisterResult.NotReady -> null
        }
    }

    private suspend fun candidateEffect(event: AnalysisEvent, decision: RegistrationDecision): AppliedEffect {
        val amount = decision.amount?.takeIf { it > 0 }
        val currency = decision.currency?.takeIf { it.length == 3 }
        if (amount == null || currency == null) return AppliedEffect(DecisionEffect.UNUSABLE)
        val parserId = (event.parser as? ParserEvidence.Parsed)?.snapshot?.parserId ?: "ai-interpretation"
        val merchant = decision.merchant?.takeIf(String::isNotBlank)
        val candidate = candidateFrom(
            event, amount, currency,
            // Sin comercio se usa el nombre de la app, marcado como no confiable (como el flujo de siempre).
            merchant ?: event.notification.applicationName,
            merchant != null && decision.merchantConfident,
            parserId,
        )
        val id = createAndAnnounce(candidate) ?: return AppliedEffect(DecisionEffect.DUPLICATE)
        return AppliedEffect(DecisionEffect.CANDIDATE_CREATED, candidateId = id)
    }

    private fun candidateFrom(
        event: AnalysisEvent,
        amount: Long,
        currency: String,
        merchant: String,
        merchantConfident: Boolean,
        parserId: String,
    ) = ExpenseCandidate(
        amount = amount,
        currency = currency,
        merchant = merchant,
        merchantConfident = merchantConfident,
        occurredAt = event.notification.timestamp,
        detectedAt = event.notification.timestamp,
        sourceType = ExpenseSource.NOTIFICATION,
        sourceApp = event.notification.applicationName,
        parserId = parserId,
    )

    /** Crea el candidato (null si ya habia uno equivalente) y avisa al usuario si sigue pendiente. */
    private suspend fun createAndAnnounce(candidate: ExpenseCandidate): Long? {
        val id = createCandidate(candidate) ?: return null
        if (findPendingCandidate(id) == null) return id
        try {
            announcer.announce(candidate.copy(id = id))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.w(TAG, "No se pudo publicar el aviso del candidato $id: ${error.javaClass.simpleName}")
        }
        return id
    }

    private suspend fun record(
        event: AnalysisEvent,
        mode: AnalysisMode,
        decision: RegistrationDecision?,
        applied: AppliedEffect,
        results: List<InterpretationResult>,
        errorClass: String?,
    ) {
        val entry = DecisionAudit(
            eventKey = event.key,
            decidedAt = now(),
            mode = mode,
            sourceApp = event.notification.applicationName,
            outcome = decision?.outcome,
            reasons = decision?.reasons.orEmpty(),
            effect = applied.effect,
            expenseId = applied.expenseId,
            candidateId = applied.candidateId,
            interpretations = results,
            errorClass = errorClass,
        )
        audit.record(entry)
        AppLog.i(
            TAG,
            "evento=${AnalysisEventKey.short(event.key)} modo=$mode app=${event.notification.packageName} " +
                "decision=${decision?.outcome ?: "-"} motivos=${entry.reasons.joinToString(",").ifEmpty { "-" }} " +
                "efecto=${applied.effect} gasto=${applied.expenseId ?: "-"} candidato=${applied.candidateId ?: "-"} " +
                "modelo=${results.firstOrNull()?.modelId ?: "-"} ms=${results.joinToString("+") { it.durationMillis.toString() }.ifEmpty { "-" }}" +
                (errorClass?.let { " error=$it" } ?: ""),
        )
    }

    private data class AppliedEffect(
        val effect: DecisionEffect,
        val expenseId: Long? = null,
        val candidateId: Long? = null,
    )
}
