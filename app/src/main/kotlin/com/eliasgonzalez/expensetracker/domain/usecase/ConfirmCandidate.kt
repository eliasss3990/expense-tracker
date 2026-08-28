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
 * El chequeo de estado + la escritura van dentro de un Mutex (mismo
 * motivo que CreateCandidate): sin esto, dos confirmaciones casi
 * simultaneas del mismo candidato (ej. un doble tap accidental en la
 * notificacion) leen PENDING antes de que cualquiera de las dos alcance
 * a marcarlo ACCEPTED, y las dos terminan registrando un Expense.
 */
class ConfirmCandidate(
    private val candidates: CandidateRepository,
    private val registerExpense: RegisterExpense,
    private val activity: ActivityRepository,
) {
    private val mutex = Mutex()

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
