package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisEvent
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisMode
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisModeProvider
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisEventKey
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisQueueRepository
import com.eliasgonzalez.expensetracker.domain.analysis.CategoryChoice
import com.eliasgonzalez.expensetracker.domain.analysis.CandidateAnnouncer
import com.eliasgonzalez.expensetracker.domain.analysis.ModelAvailability
import com.eliasgonzalez.expensetracker.domain.analysis.ReviewNoticeAnnouncer
import com.eliasgonzalez.expensetracker.domain.analysis.SourceTrust
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
import com.eliasgonzalez.expensetracker.domain.model.CategoryIds
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
    private val resolveCategory: ResolveCategory,
    private val announcer: CandidateAnnouncer,
    private val noticeAnnouncer: ReviewNoticeAnnouncer = ReviewNoticeAnnouncer { },
    private val modelAvailability: ModelAvailability = ModelAvailability { true },
    private val audit: DecisionAuditRepository,
    private val modeProvider: AnalysisModeProvider,
    private val requireCrossCheck: Boolean = true,
    private val maxAttempts: Int = 3,
    private val retentionMillis: Long = 30L * 24 * 60 * 60 * 1000,
    /** Pide un nuevo drain dentro de N ms: sin esto un reintento esperaria al proximo aviso o a reiniciar la app. */
    private val scheduleRetry: (delayMillis: Long) -> Unit = {},
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
        // Los PROCESSING que queden aca son de un drain abortado (cancelacion, excepcion): drainMutex asegura
        // que ningun otro drain esta corriendo, asi que vuelven a la cola en vez de quedar trabados.
        queue.recoverProcessing()
        if (!modelAvailability.isReady()) {
            drainWithoutModel()
            return@withLock
        }
        giveUpExhausted()
        val deferred = mutableMapOf<Long, Int>() // id -> numero de intento que acaba de fallar
        while (true) {
            val item = queue.nextPending(deferred.keys, maxAttempts) ?: break
            if (!queue.markProcessing(item.id)) continue
            val finished = try {
                process(item)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Una falla inesperada (disco lleno, base bloqueada) no debe tumbar la app ni frenar la cola.
                AppLog.e(TAG, "evento=${AnalysisEventKey.short(item.event.key)} fallo inesperado: ${error.javaClass.simpleName}", error)
                runCatching { queue.markRetry(item.id, error.javaClass.simpleName) }
                false
            }
            if (!finished) deferred[item.id] = item.attempts + 1
        }
        // Lo que gasto su ultimo intento en esta pasada no puede quedar huerfano.
        giveUpExhausted()
        if (deferred.isNotEmpty() && queue.nextPending(emptySet(), maxAttempts) != null) {
            scheduleRetry(retryDelayMillis(deferred.values.max()))
        }
    }

    /**
     * El modelo ya no esta (se borro o fallo la verificacion) con avisos encolados cuando si estaba: no hay quien los
     * analice, asi que salen por el flujo de siempre en vez de quedar pendientes para siempre.
     */
    private suspend fun drainWithoutModel() {
        while (true) {
            val item = queue.nextPending(emptySet(), Int.MAX_VALUE) ?: break
            if (!queue.markProcessing(item.id)) continue
            try {
                giveUp(item, effectiveMode(item.event), "ModelUnavailable")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.e(TAG, "evento=${AnalysisEventKey.short(item.event.key)} sin modelo y no se pudo resolver: ${error.javaClass.simpleName}", error)
                runCatching { queue.markRetry(item.id, error.javaClass.simpleName, refundAttempt = true) }
                break
            }
        }
    }

    /**
     * Sombra solo vale si el flujo de siempre YA creo el candidato al llegar el aviso. Si el aviso se encolo en
     * Asistido/Automatico y despues cambio el modo, el analisis es su unica via: se trata como Asistido.
     */
    private fun effectiveMode(event: AnalysisEvent): AnalysisMode {
        val mode = modeProvider.currentMode()
        return if (mode == AnalysisMode.SHADOW && !event.legacyHandled) AnalysisMode.ASSISTED else mode
    }

    /** Un item que agoto sus intentos se cierra con el mismo respaldo que una falla del modelo. */
    private suspend fun giveUpExhausted() {
        while (true) {
            val item = queue.nextExhausted(maxAttempts) ?: break
            try {
                giveUp(item, effectiveMode(item.event), "AttemptsExhausted")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Si ni siquiera se puede cerrar, no insistir en un bucle: se vuelve a intentar en el proximo drain.
                AppLog.e(TAG, "no se pudo cerrar un evento agotado: ${error.javaClass.simpleName}", error)
                break
            }
        }
    }

    private fun retryDelayMillis(attempt: Int): Long =
        minOf(RETRY_BASE_MILLIS shl (attempt - 1).coerceIn(0, 20), RETRY_MAX_MILLIS)

    /** true si el item quedo terminado; false si se reintentara mas tarde. */
    private suspend fun process(item: QueuedAnalysis): Boolean {
        val event = item.event
        val mode = effectiveMode(event)
        val results = try {
            interpret(event)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return handleInterpreterFailure(item, mode, error)
        }

        val decision = decide(event.notification, event.parser, results, RegistrationPolicy(event.trust, requireCrossCheck))
        val category = if (decision.outcome == RegistrationOutcome.DISCARD) {
            null
        } else {
            resolveCategory.resolve(decision.merchant, results.first().interpretation.categoryId)
        }
        val effect = try {
            apply(mode, event, decision, category)
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
        // El efecto YA se aplico: si la auditoria falla no se reintenta el efecto, solo se deja el rastro en el log.
        try {
            record(event, mode, decision, effect, results, null, category)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.e(TAG, "evento=${AnalysisEventKey.short(event.key)} no se pudo auditar: ${error.javaClass.simpleName}", error)
        }
        queue.markDone(item.id)
        return true
    }

    private suspend fun interpret(event: AnalysisEvent): List<InterpretationResult> {
        val categories = resolveCategory.options()
        val primary = interpreter.interpret(InterpretationRequest(event.notification, event.parser, InterpretationPass.PRIMARY, categories))
        if (!requireCrossCheck) return listOf(primary)
        val cross = interpreter.interpret(InterpretationRequest(event.notification, event.parser, InterpretationPass.CROSS_CHECK, categories))
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
        giveUp(item, mode, errorClass)
        return true
    }

    /**
     * Agotados los reintentos: nunca perder el aviso. Con parser, sale el candidato de siempre (salvo en
     * Sombra, donde el flujo de siempre ya lo creo al llegar).
     */
    private suspend fun giveUp(item: QueuedAnalysis, mode: AnalysisMode, errorClass: String) {
        val event = item.event
        val parser = (event.parser as? ParserEvidence.Parsed)?.snapshot
        val effect = when {
            mode == AnalysisMode.SHADOW -> AppliedEffect(DecisionEffect.SHADOW_ONLY)
            parser == null -> AppliedEffect(DecisionEffect.GAVE_UP)
            else -> {
                val candidate = candidateFrom(
                    event, parser.amount, parser.currency, parser.merchant, parser.merchantConfident, parser.parserId,
                    confidence = parser.confidence,
                )
                val id = createAndAnnounce(candidate)
                AppliedEffect(if (id != null) DecisionEffect.FALLBACK_LEGACY else DecisionEffect.DUPLICATE, candidateId = id)
            }
        }
        try {
            record(event, mode, null, effect, emptyList(), errorClass)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.e(TAG, "evento=${AnalysisEventKey.short(event.key)} no se pudo auditar: ${error.javaClass.simpleName}", error)
        }
        queue.markGaveUp(item.id, errorClass)
    }

    /** null = no terminal (hay que reintentar). */
    private suspend fun apply(
        mode: AnalysisMode,
        event: AnalysisEvent,
        decision: RegistrationDecision,
        category: CategoryChoice?,
    ): AppliedEffect? {
        if (mode == AnalysisMode.SHADOW) return AppliedEffect(DecisionEffect.SHADOW_ONLY)
        return when (decision.outcome) {
            RegistrationOutcome.DISCARD -> AppliedEffect(DecisionEffect.DISCARDED)
            RegistrationOutcome.AUTO_REGISTER ->
                if (mode == AnalysisMode.AUTOMATIC) autoRegisterEffect(event, decision, category!!) else candidateEffect(event, decision, category!!)
            RegistrationOutcome.REVIEW -> candidateEffect(event, decision, category!!)
        }
    }

    private suspend fun autoRegisterEffect(event: AnalysisEvent, decision: RegistrationDecision, category: CategoryChoice): AppliedEffect? {
        val result = autoRegister(
            eventKey = event.key,
            amount = decision.amount!!,
            currency = decision.currency!!,
            merchant = decision.merchant!!,
            merchantConfident = decision.merchantConfident,
            occurredAt = event.notification.timestamp,
            sourceApp = event.notification.applicationName,
            categoryId = category.categoryId,
        )
        return when (result) {
            is AutoRegisterResult.Registered -> AppliedEffect(DecisionEffect.AUTO_REGISTERED, expenseId = result.expenseId)
            AutoRegisterResult.Duplicate -> AppliedEffect(DecisionEffect.DUPLICATE)
            AutoRegisterResult.NotReady -> null
        }
    }

    private suspend fun candidateEffect(event: AnalysisEvent, decision: RegistrationDecision, category: CategoryChoice): AppliedEffect {
        val amount = decision.amount?.takeIf { it > 0 }
        val currency = decision.currency?.takeIf { it.length == 3 }
        if (amount == null || currency == null) {
            // Sin importe utilizable no hay candidato posible. De un banco el aviso NO se pierde: se le avisa al usuario.
            // De un buzon (un correo cualquiera) o una app no confiable seria ruido: queda solo en la auditoria.
            if (event.trust != SourceTrust.BANK) return AppliedEffect(DecisionEffect.IGNORED_UNREADABLE)
            try {
                noticeAnnouncer.announce(event.notification.applicationName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.w(TAG, "No se pudo avisar de un aviso ilegible: ${error.javaClass.simpleName}")
            }
            return AppliedEffect(DecisionEffect.UNUSABLE)
        }
        val parserId = (event.parser as? ParserEvidence.Parsed)?.snapshot?.parserId ?: "ai-interpretation"
        val merchant = decision.merchant?.takeIf(String::isNotBlank)
        val candidate = candidateFrom(
            event, amount, currency,
            // Sin comercio se usa el nombre de la app, marcado como no confiable (como el flujo de siempre).
            merchant ?: event.notification.applicationName,
            merchant != null && decision.merchantConfident,
            parserId,
            category.categoryId,
            // Es la confianza del PARSER; sin parser no hay un valor honesto que poner.
            confidence = (event.parser as? ParserEvidence.Parsed)?.snapshot?.confidence ?: 0.0,
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
        categoryId: String = CategoryIds.OTHER,
        confidence: Double = 0.0,
    ) = ExpenseCandidate(
        amount = amount,
        currency = currency,
        merchant = merchant,
        merchantConfident = merchantConfident,
        categorySuggestion = categoryId,
        confidence = confidence,
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
        category: CategoryChoice? = null,
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
            categoryId = category?.categoryId,
            categorySource = category?.source,
        )
        audit.record(entry)
        AppLog.i(
            TAG,
            "evento=${AnalysisEventKey.short(event.key)} modo=$mode app=${event.notification.packageName} " +
                "decision=${decision?.outcome ?: "-"} motivos=${entry.reasons.joinToString(",").ifEmpty { "-" }} " +
                "efecto=${applied.effect} gasto=${applied.expenseId ?: "-"} candidato=${applied.candidateId ?: "-"} " +
                "categoria=${category?.let { "${it.categoryId}/${it.source}" } ?: "-"} modelo=${results.firstOrNull()?.modelId ?: "-"} ms=${results.joinToString("+") { it.durationMillis.toString() }.ifEmpty { "-" }}" +
                (errorClass?.let { " error=$it" } ?: ""),
        )
    }

    private companion object {
        const val RETRY_BASE_MILLIS = 60_000L
        const val RETRY_MAX_MILLIS = 15 * 60_000L
    }

    private data class AppliedEffect(
        val effect: DecisionEffect,
        val expenseId: Long? = null,
        val candidateId: Long? = null,
    )
}
