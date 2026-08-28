package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decorador de prueba que introduce un delay antes de persistir el update.
 * Simula lo que pasaria con un repositorio real (ej. Room) cuya escritura
 * tarda lo suficiente como para que dos llamadas concurrentes lean el mismo
 * estado PENDING antes de que cualquiera de las dos termine de escribir.
 * No toca FakeRepositories.kt - es un wrapper solo para este archivo.
 */
private class DelayedUpdateCandidateRepositoryForConfirm(
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

class ConfirmCandidateTest {

    private fun buildUseCase(
        candidateRepo: CandidateRepository? = null,
    ): Triple<ConfirmCandidate, CandidateRepository, FakeExpenseRepository> {
        val candidates = candidateRepo ?: FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        return Triple(ConfirmCandidate(candidates, registerExpense, activity), candidates, expenses)
    }

    private fun pendingCandidate(
        amount: Long = 85_000,
        merchant: String = "McDonalds",
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
    fun `acepta un candidato pendiente y crea el gasto`() = runTest {
        val (confirmCandidate, candidates, expenses) = buildUseCase()
        val candidateId = candidates.save(pendingCandidate())

        val expenseId = confirmCandidate(candidateId)

        assertEquals(1, expenses.expenses.value.size)
        assertEquals(expenseId, expenses.expenses.value.first().id)
        assertEquals(CandidateStatus.ACCEPTED, candidates.findById(candidateId)?.status)
    }

    @Test
    fun `es idempotente - aceptar dos veces no duplica el gasto`() = runTest {
        val (confirmCandidate, candidates, expenses) = buildUseCase()
        val candidateId = candidates.save(pendingCandidate())

        confirmCandidate(candidateId)
        val secondResult = confirmCandidate(candidateId)

        assertNull(secondResult)
        assertEquals(1, expenses.expenses.value.size)
    }

    @Test
    fun `aceptar dos veces secuencial no duplica la entrada de actividad`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val confirmCandidate = ConfirmCandidate(candidates, registerExpense, activity)
        val candidateId = candidates.save(pendingCandidate())

        confirmCandidate(candidateId)
        confirmCandidate(candidateId)

        val acceptedEntries = activity.recent.value.filter { it.type == ActivityType.CANDIDATE_ACCEPTED }
        assertEquals(1, acceptedEntries.size)
    }

    @Test
    fun `candidato inexistente no rompe nada`() = runTest {
        val (confirmCandidate, _, expenses) = buildUseCase()

        val result = confirmCandidate(999L)

        assertNull(result)
        assertEquals(0, expenses.expenses.value.size)
    }

    @Test
    fun `candidato inexistente no genera entradas de actividad`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val confirmCandidate = ConfirmCandidate(candidates, registerExpense, activity)

        confirmCandidate(999L)

        assertEquals(0, activity.recent.value.size)
    }

    @Test
    fun `aceptar un candidato ya rechazado es un no-op`() = runTest {
        val (confirmCandidate, candidates, expenses) = buildUseCase()
        val candidateId = candidates.save(pendingCandidate(status = CandidateStatus.REJECTED))

        val result = confirmCandidate(candidateId)

        assertNull(result)
        assertEquals(0, expenses.expenses.value.size)
        assertEquals(CandidateStatus.REJECTED, candidates.findById(candidateId)?.status)
    }

    @Test
    fun `aceptar un candidato ya aceptado es un no-op`() = runTest {
        val (confirmCandidate, candidates, expenses) = buildUseCase()
        val candidateId = candidates.save(pendingCandidate(status = CandidateStatus.ACCEPTED))

        val result = confirmCandidate(candidateId)

        assertNull(result)
        assertEquals(0, expenses.expenses.value.size)
    }

    @Test
    fun `aceptar un candidato editado es un no-op - no re-confirma un edit`() = runTest {
        val (confirmCandidate, candidates, expenses) = buildUseCase()
        val candidateId = candidates.save(pendingCandidate(status = CandidateStatus.EDITED))

        val result = confirmCandidate(candidateId)

        assertNull(result)
        assertEquals(0, expenses.expenses.value.size)
        assertEquals(CandidateStatus.EDITED, candidates.findById(candidateId)?.status)
    }

    @Test
    fun `confirmar con monto cero no crashea y crea el gasto tal cual`() = runTest {
        val (confirmCandidate, candidates, expenses) = buildUseCase()
        val candidateId = candidates.save(pendingCandidate(amount = 0L))

        val expenseId = confirmCandidate(candidateId)

        assertNotNull(expenseId)
        assertEquals(0L, expenses.expenses.value.first().amount)
    }

    @Test
    fun `confirmar con monto negativo no crashea y preserva el signo`() = runTest {
        val (confirmCandidate, candidates, expenses) = buildUseCase()
        val candidateId = candidates.save(pendingCandidate(amount = -50_000L))

        val expenseId = confirmCandidate(candidateId)

        assertNotNull(expenseId)
        assertEquals(-50_000L, expenses.expenses.value.first().amount)
    }

    @Test
    fun `confirmar con monto extremo Long MAX_VALUE no crashea`() = runTest {
        val (confirmCandidate, candidates, expenses) = buildUseCase()
        val candidateId = candidates.save(pendingCandidate(amount = Long.MAX_VALUE))

        val expenseId = confirmCandidate(candidateId)

        assertNotNull(expenseId)
        assertEquals(Long.MAX_VALUE, expenses.expenses.value.first().amount)
    }

    @Test
    fun `la entrada de actividad al confirmar tiene el tipo y los ids correctos`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val confirmCandidate = ConfirmCandidate(candidates, registerExpense, activity)
        val candidateId = candidates.save(pendingCandidate())

        val expenseId = confirmCandidate(candidateId)

        val entry = activity.recent.value.first { it.type == ActivityType.CANDIDATE_ACCEPTED }
        assertEquals(candidateId, entry.candidateId)
        assertEquals(expenseId, entry.expenseId)
    }

    @Test
    fun `condicion de carrera - dos confirmaciones concurrentes del mismo candidato pueden duplicar el gasto`() = runTest {
        // BUG REAL: a diferencia de CreateCandidate (que usa Mutex), ConfirmCandidate
        // no protege la seccion critica lectura-de-estado + escritura-de-estado.
        // Con un repositorio cuya escritura tarda (delay simulando I/O real), dos
        // llamadas concurrentes al mismo candidatoId leen PENDING antes de que
        // cualquiera de las dos alcance a marcarlo ACCEPTED, y ambas terminan
        // registrando un Expense. Ver ConfirmCandidate.kt:20-22.
        val realCandidates = FakeCandidateRepository()
        val delayedCandidates = DelayedUpdateCandidateRepositoryForConfirm(realCandidates)
        val (confirmCandidate, _, expenses) = buildUseCase(delayedCandidates)
        val candidateId = realCandidates.save(pendingCandidate())

        val first = async { confirmCandidate(candidateId) }
        val second = async { confirmCandidate(candidateId) }
        val results = listOfNotNull(first.await(), second.await())

        // Comportamiento actual (documentado, no corregido aqui): ambas llamadas
        // "ganan" la carrera y se crean dos Expense para el mismo candidato.
        assertEquals(2, results.size)
        assertEquals(2, expenses.expenses.value.size)
        assertTrue(
            "Se esperaba que la carrera produjera un duplicado real (bug conocido)",
            expenses.expenses.value.all { it.sourceReference == candidateId },
        )
    }

    @Test
    fun `alta concurrencia sobre distintos candidatos no se cruzan entre si`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val confirmCandidate = ConfirmCandidate(candidates, registerExpense, activity)

        val n = 10
        val ids = (0 until n).map { i -> candidates.save(pendingCandidate(amount = 1_000L + i, merchant = "Comercio $i")) }
        val jobs = ids.map { id -> async { confirmCandidate(id) } }
        val results = jobs.awaitAll()

        assertEquals(n, results.filterNotNull().size)
        assertEquals(n, expenses.expenses.value.size)
    }
}
