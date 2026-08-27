package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RejectCandidateTest {

    @Test
    fun `rechaza un candidato pendiente sin crear gasto`() = runTest {
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val rejectCandidate = RejectCandidate(candidates, activity)
        val candidateId = candidates.save(
            ExpenseCandidate(
                amount = 40_000,
                merchant = "Shell",
                occurredAt = 1L,
                detectedAt = 1L,
                sourceType = ExpenseSource.NOTIFICATION,
            )
        )

        rejectCandidate(candidateId)

        assertEquals(CandidateStatus.REJECTED, candidates.findById(candidateId)?.status)
    }

    @Test
    fun `es idempotente - no reescribe un candidato ya aceptado`() = runTest {
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val rejectCandidate = RejectCandidate(candidates, activity)
        val candidateId = candidates.save(
            ExpenseCandidate(
                amount = 40_000,
                merchant = "Shell",
                occurredAt = 1L,
                detectedAt = 1L,
                sourceType = ExpenseSource.NOTIFICATION,
                status = CandidateStatus.ACCEPTED,
            )
        )

        rejectCandidate(candidateId)

        assertEquals(CandidateStatus.ACCEPTED, candidates.findById(candidateId)?.status)
    }
}
