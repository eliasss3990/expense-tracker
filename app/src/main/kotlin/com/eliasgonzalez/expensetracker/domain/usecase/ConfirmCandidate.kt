package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository

/**
 * Idempotente (seccion 60 del plan): si el candidato ya no esta PENDING,
 * no hace nada - evita duplicar el Expense si la accion llega dos veces.
 */
class ConfirmCandidate(
    private val candidates: CandidateRepository,
    private val registerExpense: RegisterExpense,
    private val activity: ActivityRepository,
) {
    suspend operator fun invoke(candidateId: Long): Long? {
        val candidate = candidates.findById(candidateId) ?: return null
        if (candidate.status != CandidateStatus.PENDING) return null

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
        return expenseId
    }
}
