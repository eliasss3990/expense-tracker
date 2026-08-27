package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository

class CreateCandidate(
    private val candidates: CandidateRepository,
    private val activity: ActivityRepository,
) {
    suspend operator fun invoke(candidate: ExpenseCandidate): Long {
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
}
