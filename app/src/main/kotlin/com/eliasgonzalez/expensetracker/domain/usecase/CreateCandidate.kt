package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import kotlin.math.abs

private const val DEDUP_WINDOW_MILLIS = 5 * 60 * 1000L

/**
 * Crea un candidato, salvo que ya exista uno muy parecido detectado hace
 * poco (mismo monto+moneda, comercio equivalente, dentro de una ventana de
 * tiempo corta) - eso pasa cuando la misma compra dispara notificaciones
 * de más de una fuente (ej. el banco y la billetera al mismo tiempo).
 * No borra información, simplemente no duplica el candidato: devuelve
 * null y el llamador no debería mostrar una segunda notificación.
 */
class CreateCandidate(
    private val candidates: CandidateRepository,
    private val activity: ActivityRepository,
) {
    suspend operator fun invoke(candidate: ExpenseCandidate): Long? {
        if (findDuplicate(candidate) != null) return null

        val id = candidates.save(candidate)
        activity.record(
            ActivityEntry(
                type = ActivityType.CANDIDATE_CREATED,
                candidateId = id,
                timestamp = candidate.detectedAt,
                summary = "${candidate.merchant} — ₲${"%,d".format(candidate.amount)}",
            )
        )
        return id
    }

    private fun findDuplicate(candidate: ExpenseCandidate): ExpenseCandidate? =
        candidates.candidates.value.find { existing ->
            existing.amount == candidate.amount &&
                existing.currency == candidate.currency &&
                existing.merchant.normalizedForMatch() == candidate.merchant.normalizedForMatch() &&
                abs(existing.detectedAt - candidate.detectedAt) <= DEDUP_WINDOW_MILLIS
        }

    private fun String.normalizedForMatch(): String =
        trim().uppercase().filter { it.isLetterOrDigit() }
}
