package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mismo decorador de prueba que en ConfirmCandidateTest: agrega un delay
 * antes de persistir el update, para simular una escritura de I/O real y
 * poder exponer condiciones de carrera reales entre lectura y escritura de
 * estado. No toca FakeRepositories.kt.
 */
private class DelayedUpdateCandidateRepositoryForReject(
    private val delegate: CandidateRepository,
    private val delayMillis: Long = 10,
) : CandidateRepository {
    override val candidates: StateFlow<List<ExpenseCandidate>> get() = delegate.candidates
    override suspend fun save(candidate: ExpenseCandidate): Long = delegate.save(candidate)
    override suspend fun update(candidate: ExpenseCandidate) {
        delay(delayMillis)
        delegate.update(candidate)
    }
    override fun findById(id: Long): ExpenseCandidate? = delegate.findById(id)
}

class RejectCandidateTest {

    private fun pendingCandidate(
        amount: Long = 40_000,
        merchant: String = "Shell",
        status: CandidateStatus = CandidateStatus.PENDING,
    ) = ExpenseCandidate(
        amount = amount,
        merchant = merchant,
        occurredAt = 1L,
        detectedAt = 1L,
        sourceType = ExpenseSource.NOTIFICATION,
        status = status,
    )

    @Test
    fun `rechaza un candidato pendiente sin crear gasto`() = runTest {
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val rejectCandidate = RejectCandidate(candidates, activity)
        val candidateId = candidates.save(pendingCandidate())

        rejectCandidate(candidateId)

        assertEquals(CandidateStatus.REJECTED, candidates.findById(candidateId)?.status)
    }

    @Test
    fun `es idempotente - no reescribe un candidato ya aceptado`() = runTest {
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val rejectCandidate = RejectCandidate(candidates, activity)
        val candidateId = candidates.save(pendingCandidate(status = CandidateStatus.ACCEPTED))

        rejectCandidate(candidateId)

        assertEquals(CandidateStatus.ACCEPTED, candidates.findById(candidateId)?.status)
    }

    @Test
    fun `rechazar dos veces el mismo candidato no duplica la entrada de actividad`() = runTest {
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val rejectCandidate = RejectCandidate(candidates, activity)
        val candidateId = candidates.save(pendingCandidate())

        rejectCandidate(candidateId)
        rejectCandidate(candidateId)

        val rejectedEntries = activity.recent.value.filter { it.type == ActivityType.CANDIDATE_REJECTED }
        assertEquals(1, rejectedEntries.size)
    }

    @Test
    fun `rechazar un candidato ya editado es un no-op`() = runTest {
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val rejectCandidate = RejectCandidate(candidates, activity)
        val candidateId = candidates.save(pendingCandidate(status = CandidateStatus.EDITED))

        rejectCandidate(candidateId)

        assertEquals(CandidateStatus.EDITED, candidates.findById(candidateId)?.status)
        assertEquals(0, activity.recent.value.size)
    }

    @Test
    fun `rechazar un candidato ya rechazado no genera una segunda entrada`() = runTest {
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val rejectCandidate = RejectCandidate(candidates, activity)
        val candidateId = candidates.save(pendingCandidate(status = CandidateStatus.REJECTED))

        rejectCandidate(candidateId)

        assertEquals(0, activity.recent.value.size)
    }

    @Test
    fun `candidato inexistente no rompe nada y no genera actividad`() = runTest {
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val rejectCandidate = RejectCandidate(candidates, activity)

        rejectCandidate(999L)

        assertNull(candidates.findById(999L))
        assertEquals(0, activity.recent.value.size)
    }

    @Test
    fun `rechazar con monto extremo no crashea por el formato del summary`() = runTest {
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val rejectCandidate = RejectCandidate(candidates, activity)
        val candidateId = candidates.save(pendingCandidate(amount = Long.MIN_VALUE))

        rejectCandidate(candidateId)

        assertEquals(CandidateStatus.REJECTED, candidates.findById(candidateId)?.status)
    }

    @Test
    fun `la entrada de actividad al rechazar tiene el tipo y el candidateId correctos, sin expenseId`() = runTest {
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val rejectCandidate = RejectCandidate(candidates, activity)
        val candidateId = candidates.save(pendingCandidate())

        rejectCandidate(candidateId)

        val entry = activity.recent.value.first { it.type == ActivityType.CANDIDATE_REJECTED }
        assertEquals(candidateId, entry.candidateId)
        assertNull(entry.expenseId)
    }

    @Test
    fun `condicion de carrera - dos rechazos concurrentes del mismo candidato pueden duplicar la entrada de actividad`() = runTest {
        // BUG REAL, mismo patron que en ConfirmCandidate: no hay proteccion (mutex)
        // entre la lectura del estado PENDING y la escritura de REJECTED. Con una
        // escritura que tarda (delay simulando I/O real), dos llamadas concurrentes
        // al mismo candidato pueden pasar ambas el chequeo de estado antes de que
        // cualquiera termine de persistir, y las dos registran actividad de rechazo.
        // Ver RejectCandidate.kt:14-15.
        val realCandidates = FakeCandidateRepository()
        val delayedCandidates = DelayedUpdateCandidateRepositoryForReject(realCandidates)
        val activity = FakeActivityRepository()
        val rejectCandidate = RejectCandidate(delayedCandidates, activity)
        val candidateId = realCandidates.save(pendingCandidate())

        val first = async { rejectCandidate(candidateId) }
        val second = async { rejectCandidate(candidateId) }
        first.await()
        second.await()

        val rejectedEntries = activity.recent.value.filter { it.type == ActivityType.CANDIDATE_REJECTED }
        // Comportamiento actual (documentado, no corregido aqui): la carrera produce
        // dos entradas de actividad para el mismo candidato en lugar de una sola.
        assertEquals(2, rejectedEntries.size)
        assertEquals(CandidateStatus.REJECTED, realCandidates.findById(candidateId)?.status)
    }
}
