package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CreateCandidateTest {

    private fun candidate(
        merchant: String,
        detectedAt: Long,
        amount: Long = 85_000,
        merchantConfident: Boolean = true,
    ) = ExpenseCandidate(
        amount = amount,
        merchant = merchant,
        merchantConfident = merchantConfident,
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

    @Test
    fun `no duplica cuando una de las fuentes no confia en el comercio - ueno app + mail`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        // Notificación push del banco: sin beneficiario, merchant de relleno.
        createCandidate(
            candidate("Transferencia Ueno Bank", detectedAt = 0L, amount = 800_000, merchantConfident = false)
        )
        // Mail de confirmación, con el beneficiario real - mismo monto, casi mismo instante.
        val secondId = candidate("ROBERTO GONZALEZ QUIONEZ", detectedAt = 500L, amount = 800_000)
            .let { createCandidate(it) }

        assertNull(secondId)
        assertEquals(1, candidates.candidates.value.size)
    }

    @Test
    fun `dos comercios confiables pero distintos no se funden aunque una fuente sea debil en otro campo`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("Kiosco", detectedAt = 0L, amount = 25_000))
        val secondId = createCandidate(candidate("Farmacia", detectedAt = 1_000L, amount = 25_000))

        assertNotNull(secondId)
        assertEquals(2, candidates.candidates.value.size)
    }

    @Test
    fun `dos notificaciones casi simultaneas no crean dos candidatos - condicion de carrera`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        val first = async { createCandidate(candidate("GONZALEZ MEZA", detectedAt = 1_000L)) }
        val second = async { createCandidate(candidate("GONZALEZ MEZA", detectedAt = 1_010L)) }

        val results = listOfNotNull(first.await(), second.await())
        assertEquals(1, results.size)
        assertEquals(1, candidates.candidates.value.size)
    }
}
