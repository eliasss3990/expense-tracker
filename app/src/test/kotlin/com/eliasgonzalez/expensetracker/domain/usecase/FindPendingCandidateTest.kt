package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindPendingCandidateTest {

    private fun candidate(status: CandidateStatus) = ExpenseCandidate(
        amount = 5_000,
        merchant = "Kiosco",
        categorySuggestion = "other",
        status = status,
        occurredAt = 1L,
        detectedAt = 1L,
        sourceType = ExpenseSource.NOTIFICATION,
    )

    @Test
    fun `devuelve el candidato si sigue pendiente`() = runTest {
        val candidates = FakeCandidateRepository()
        val id = candidates.save(candidate(CandidateStatus.PENDING))
        val findPendingCandidate = FindPendingCandidate(candidates)

        assertEquals(id, findPendingCandidate(id)?.id)
    }

    @Test
    fun `devuelve null si el candidato ya fue aceptado`() = runTest {
        val candidates = FakeCandidateRepository()
        val id = candidates.save(candidate(CandidateStatus.ACCEPTED))
        val findPendingCandidate = FindPendingCandidate(candidates)

        assertNull(findPendingCandidate(id))
    }

    @Test
    fun `devuelve null si el candidato ya fue rechazado`() = runTest {
        val candidates = FakeCandidateRepository()
        val id = candidates.save(candidate(CandidateStatus.REJECTED))
        val findPendingCandidate = FindPendingCandidate(candidates)

        assertNull(findPendingCandidate(id))
    }

    @Test
    fun `devuelve null si el candidato ya fue editado (y por lo tanto aceptado)`() = runTest {
        val candidates = FakeCandidateRepository()
        val id = candidates.save(candidate(CandidateStatus.EDITED))
        val findPendingCandidate = FindPendingCandidate(candidates)

        assertNull(findPendingCandidate(id))
    }

    @Test
    fun `devuelve null si el id no existe`() = runTest {
        val candidates = FakeCandidateRepository()
        val findPendingCandidate = FindPendingCandidate(candidates)

        assertNull(findPendingCandidate(999L))
    }

    @Test
    fun `devuelve null para un id negativo o cero`() = runTest {
        val candidates = FakeCandidateRepository()
        val findPendingCandidate = FindPendingCandidate(candidates)

        assertNull(findPendingCandidate(0L))
        assertNull(findPendingCandidate(-1L))
    }
}
