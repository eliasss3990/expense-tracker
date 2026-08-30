package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Idempotente: si el candidato ya no esta PENDING, no hace nada - evita
 * duplicar el Expense si la accion llega dos veces.
 *
 * El chequeo de estado + la escritura van dentro de un Mutex COMPARTIDO
 * con CreateCandidate/EditCandidate/RejectCandidate (ver el comentario
 * en CreateCandidate.kt para el motivo de compartirlo en vez de uno por
 * clase): sin esto, dos acciones casi simultaneas sobre el mismo
 * candidato (ej. un doble tap accidental en la notificacion, o
 * confirmar y editar el mismo candidato desde dos lugares a la vez)
 * leen PENDING antes de que cualquiera alcance a cambiarle el estado, y
 * mas de una termina registrando un Expense para el mismo candidato.
 */
class ConfirmCandidate(
    private val candidates: CandidateRepository,
    private val registerExpense: RegisterExpense,
    private val activity: ActivityRepository,
    private val mutex: Mutex = Mutex(),
) {
    suspend operator fun invoke(candidateId: Long): Long? = mutex.withLock {
        val candidate = candidates.findById(candidateId) ?: return@withLock null
        if (candidate.status != CandidateStatus.PENDING) return@withLock null

        val now = System.currentTimeMillis()
        val expenseId = registerExpense(
            Expense(
                amount = candidate.amount,
                currency = candidate.currency,
                merchant = candidate.merchant,
                categoryId = candidate.categorySuggestion,
                description = candidate.description,
                occurredAt = candidate.occurredAt,
                createdAt = now,
                source = candidate.sourceType,
                sourceReference = candidate.id,
            )
        )
        candidates.update(candidate.copy(status = CandidateStatus.ACCEPTED))
        activity.record(
            ActivityEntry(
                type = ActivityType.CANDIDATE_ACCEPTED,
                expenseId = expenseId,
                candidateId = candidateId,
                timestamp = now,
                summary = "${candidate.merchant} — ₲${"%,d".format(candidate.amount)}",
            )
        )
        expenseId
    }
}
