package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository

class RejectCandidate(
    private val candidates: CandidateRepository,
    private val activity: ActivityRepository,
) {
    suspend operator fun invoke(candidateId: Long) {
        val candidate = candidates.findById(candidateId) ?: return
        if (candidate.status != CandidateStatus.PENDING) return

        candidates.update(candidate.copy(status = CandidateStatus.REJECTED))
        activity.record(
            ActivityEntry(
                type = ActivityType.CANDIDATE_REJECTED,
                candidateId = candidateId,
                timestamp = System.currentTimeMillis(),
                summary = "${candidate.merchant} — ₲${"%,d".format(candidate.amount)}",
            )
        )
    }
}
