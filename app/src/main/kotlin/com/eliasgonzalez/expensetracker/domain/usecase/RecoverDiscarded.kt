package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.analysis.CandidateAnnouncer
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionAuditRepository
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionEffect
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationPass
import com.eliasgonzalez.expensetracker.domain.log.AppLog
import com.eliasgonzalez.expensetracker.domain.model.CategoryIds
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.CancellationException

sealed interface RecoverResult {
    data class Recovered(val candidateId: Long) : RecoverResult

    /** No es un descarte, no hay interpretacion o no tenia ningun importe del cual armar el candidato. */
    data object NotRecoverable : RecoverResult

    /** Ya hay un candidato o gasto equivalente. */
    data object Duplicate : RecoverResult
}

/**
 * Descartar es la unica decision del analisis que hace desaparecer un aviso, y la hace un modelo que puede
 * equivocarse. Esto la vuelve reversible: toma lo que el modelo interpreto y lo convierte en un candidato normal
 * para que el usuario decida. Por eso el descarte es aceptable.
 */
class RecoverDiscarded(
    private val audit: DecisionAuditRepository,
    private val createCandidate: CreateCandidate,
    private val findPendingCandidate: FindPendingCandidate,
    private val announcer: CandidateAnnouncer,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(eventKey: String): RecoverResult {
        val entry = audit.recent(SEARCH_WINDOW).firstOrNull { it.eventKey == eventKey && it.effect == DecisionEffect.DISCARDED }
            ?: return RecoverResult.NotRecoverable
        val interpretation = entry.interpretations.firstOrNull { it.pass == InterpretationPass.PRIMARY }?.interpretation
            ?: return RecoverResult.NotRecoverable
        val amount = (interpretation.operationAmount ?: interpretation.amounts.firstOrNull())
            ?.takeIf { it.value > 0 } ?: return RecoverResult.NotRecoverable

        val merchant = interpretation.merchant?.takeIf(String::isNotBlank)
        val candidate = ExpenseCandidate(
            amount = amount.value,
            currency = amount.currency ?: "PYG",
            merchant = merchant ?: entry.sourceApp,
            merchantConfident = merchant != null,
            categorySuggestion = interpretation.categoryId ?: CategoryIds.OTHER,
            occurredAt = entry.decidedAt,
            detectedAt = now(),
            sourceType = ExpenseSource.NOTIFICATION,
            sourceApp = entry.sourceApp,
            parserId = "ai-recovered",
            confidence = 0.0,
        )
        val id = createCandidate(candidate) ?: return RecoverResult.Duplicate
        if (findPendingCandidate(id) != null) {
            try {
                announcer.announce(candidate.copy(id = id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.w("RecoverDiscarded", "No se pudo avisar del candidato recuperado $id: ${error.javaClass.simpleName}")
            }
        }
        return RecoverResult.Recovered(id)
    }

    private companion object {
        const val SEARCH_WINDOW = 500
    }
}
