package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository

class EditCandidate(
    private val candidates: CandidateRepository,
    private val registerExpense: RegisterExpense,
    private val activity: ActivityRepository,
) {
    suspend operator fun invoke(candidateId: Long, amount: Long, merchant: String): Long? {
        val candidate = candidates.findById(candidateId) ?: return null
        if (candidate.status != CandidateStatus.PENDING) return null

        val now = System.currentTimeMillis()
        val expenseId = registerExpense(
            Expense(
                amount = amount,
                currency = candidate.currency,
                merchant = merchant,
                categoryId = candidate.categorySuggestion,
                occurredAt = candidate.occurredAt,
                createdAt = now,
                source = candidate.sourceType,
                sourceReference = candidate.id,
            )
        )
        candidates.update(
            candidate.copy(status = CandidateStatus.EDITED, amount = amount, merchant = merchant)
        )
        activity.record(
            ActivityEntry(
                type = ActivityType.CANDIDATE_EDITED,
                expenseId = expenseId,
                candidateId = candidateId,
                timestamp = now,
                summary = "$merchant — ₲${"%,d".format(amount)}",
            )
        )
        return expenseId
    }
}
