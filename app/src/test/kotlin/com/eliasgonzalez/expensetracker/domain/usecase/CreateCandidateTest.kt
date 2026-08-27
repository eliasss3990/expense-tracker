package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CreateCandidateTest {

    private fun candidate(merchant: String, detectedAt: Long, amount: Long = 85_000) = ExpenseCandidate(
        amount = amount,
        merchant = merchant,
        occurredAt = detectedAt,
        detectedAt = detectedAt,
        sourceType = ExpenseSource.NOTIFICATION,
    )

    @Test
    fun `crea el candidato cuando no hay duplicado`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        val id = createCandidate(candidate("McDonalds", detectedAt = 1000L))

        assertNotNull(id)
        assertEquals(1, candidates.candidates.value.size)
    }

    @Test
    fun `no duplica cuando el banco y la billetera avisan la misma compra`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("MCDONALDS", detectedAt = 1_000L))
        val secondId = createCandidate(candidate("McDonald's", detectedAt = 1_000L + 60_000L))

        assertNull(secondId)
        assertEquals(1, candidates.candidates.value.size)
    }

    @Test
    fun `si pasa la ventana de tiempo no lo considera duplicado`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("MCDONALDS", detectedAt = 0L))
        val secondId = createCandidate(candidate("MCDONALDS", detectedAt = 10 * 60_000L))

        assertNotNull(secondId)
        assertEquals(2, candidates.candidates.value.size)
    }

    @Test
    fun `distinto monto no se considera duplicado`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("MCDONALDS", detectedAt = 0L, amount = 85_000))
        val secondId = createCandidate(candidate("MCDONALDS", detectedAt = 1_000L, amount = 40_000))

        assertNotNull(secondId)
        assertEquals(2, candidates.candidates.value.size)
    }
}
