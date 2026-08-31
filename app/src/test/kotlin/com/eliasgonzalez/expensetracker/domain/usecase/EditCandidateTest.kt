package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.Category
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mismo decorador de prueba que en ConfirmCandidateTest/RejectCandidateTest:
 * agrega un delay antes de persistir el update, para simular una escritura
 * de I/O real y exponer condiciones de carrera reales entre lectura y
 * escritura de estado. No toca FakeRepositories.kt.
 */
private class DelayedUpdateCandidateRepositoryForEdit(
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

class EditCandidateTest {

    private fun pendingCandidate(
        amount: Long = 85_000,
        merchant: String = "MCDONALDS",
        categorySuggestion: String = Category.OTHER.id,
        status: CandidateStatus = CandidateStatus.PENDING,
    ) = ExpenseCandidate(
        amount = amount,
        merchant = merchant,
        categorySuggestion = categorySuggestion,
        occurredAt = 1L,
        detectedAt = 1L,
        sourceType = ExpenseSource.NOTIFICATION,
        status = status,
    )

    @Test
    fun `edita monto y comercio, crea el gasto con los valores editados`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(candidates, registerExpense, activity)

        val candidateId = candidates.save(pendingCandidate())

        editCandidate(candidateId, amount = 90_000, merchant = "McDonald's - Shopping")

        val expense = expenses.expenses.value.first()
        assertEquals(90_000, expense.amount)
        assertEquals("McDonald's - Shopping", expense.merchant)
        assertEquals(CandidateStatus.EDITED, candidates.findById(candidateId)?.status)
    }

    @Test
    fun `editar un candidato ya aceptado es un no-op`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(candidates, registerExpense, activity)
        val candidateId = candidates.save(pendingCandidate(status = CandidateStatus.ACCEPTED))

        val result = editCandidate(candidateId, amount = 1L, merchant = "Otro")

        assertNull(result)
        assertEquals(0, expenses.expenses.value.size)
        val stored = candidates.findById(candidateId)
        assertEquals(CandidateStatus.ACCEPTED, stored?.status)
        assertEquals(85_000L, stored?.amount)
        assertEquals("MCDONALDS", stored?.merchant)
    }

    @Test
    fun `editar un candidato ya rechazado es un no-op`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(candidates, registerExpense, activity)
        val candidateId = candidates.save(pendingCandidate(status = CandidateStatus.REJECTED))

        val result = editCandidate(candidateId, amount = 1L, merchant = "Otro")

        assertNull(result)
        assertEquals(0, expenses.expenses.value.size)
        assertEquals(CandidateStatus.REJECTED, candidates.findById(candidateId)?.status)
    }

    @Test
    fun `editar un candidato ya editado es un no-op - no permite re-editar`() = runTest {
        // Documentando comportamiento real: como el chequeo exige status == PENDING,
        // una vez que un candidato pasa a EDITED ya no se puede volver a editar.
        // Si el producto espera permitir multiples ediciones antes de confirmar,
        // esto es una limitacion real de EditCandidate.kt:22.
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(candidates, registerExpense, activity)
        val candidateId = candidates.save(pendingCandidate(status = CandidateStatus.EDITED))

        val result = editCandidate(candidateId, amount = 999L, merchant = "Nuevo comercio")

        assertNull(result)
        assertEquals(0, expenses.expenses.value.size)
        assertEquals("MCDONALDS", candidates.findById(candidateId)?.merchant)
    }

    @Test
    fun `editar candidato inexistente no rompe nada`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(candidates, registerExpense, activity)

        val result = editCandidate(999L, amount = 1_000L, merchant = "Alguien")

        assertNull(result)
        assertEquals(0, expenses.expenses.value.size)
        assertEquals(0, activity.recent.value.size)
    }

    @Test
    fun `editar con monto cero no crashea y se persiste tal cual`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(candidates, registerExpense, activity)
        val candidateId = candidates.save(pendingCandidate())

        val expenseId = editCandidate(candidateId, amount = 0L, merchant = "Gratis")

        assertNotNull(expenseId)
        assertEquals(0L, expenses.expenses.value.first().amount)
    }

    @Test
    fun `editar con monto negativo no crashea y no lo normaliza`() = runTest {
        // Documentando: EditCandidate no valida el signo del monto, lo persiste tal cual.
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(candidates, registerExpense, activity)
        val candidateId = candidates.save(pendingCandidate())

        editCandidate(candidateId, amount = -1_000L, merchant = "Reembolso")

        assertEquals(-1_000L, expenses.expenses.value.first().amount)
        assertEquals(-1_000L, candidates.findById(candidateId)?.amount)
    }

    @Test
    fun `editar con monto extremo Long MAX_VALUE no overflowea ni crashea`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(candidates, registerExpense, activity)
        val candidateId = candidates.save(pendingCandidate())

        val expenseId = editCandidate(candidateId, amount = Long.MAX_VALUE, merchant = "Comercio Grande")

        assertNotNull(expenseId)
        assertEquals(Long.MAX_VALUE, expenses.expenses.value.first().amount)
    }

    @Test
    fun `editar con comercio vacio persiste vacio sin fallback`() = runTest {
        // Documentando: a diferencia de categoryId (que usa ifBlank), merchant NO
        // tiene fallback en EditCandidate.kt:30 - un string vacio queda tal cual.
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(candidates, registerExpense, activity)
        val candidateId = candidates.save(pendingCandidate())

        editCandidate(candidateId, amount = 1_000L, merchant = "")

        assertEquals("", expenses.expenses.value.first().merchant)
        assertEquals("", candidates.findById(candidateId)?.merchant)
    }

    @Test
    fun `editar con comercio solo espacios en blanco no lo trimea`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(candidates, registerExpense, activity)
        val candidateId = candidates.save(pendingCandidate())

        editCandidate(candidateId, amount = 1_000L, merchant = "   ")

        assertEquals("   ", expenses.expenses.value.first().merchant)
    }

    @Test
    fun `editar con categoryId en blanco usa la sugerencia del candidato como fallback`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(candidates, registerExpense, activity)
        val candidateId = candidates.save(pendingCandidate(categorySuggestion = Category.FOOD.id))

        editCandidate(candidateId, amount = 1_000L, merchant = "Comercio", categoryId = "   ")

        assertEquals(Category.FOOD.id, expenses.expenses.value.first().categoryId)
        assertEquals(Category.FOOD.id, candidates.findById(candidateId)?.categorySuggestion)
    }

    @Test
    fun `editar con categoryId explicito lo respeta en vez del fallback`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(candidates, registerExpense, activity)
        val candidateId = candidates.save(pendingCandidate(categorySuggestion = Category.FOOD.id))

        editCandidate(candidateId, amount = 1_000L, merchant = "Comercio", categoryId = Category.TRANSPORT.id)

        assertEquals(Category.TRANSPORT.id, expenses.expenses.value.first().categoryId)
        assertEquals(Category.TRANSPORT.id, candidates.findById(candidateId)?.categorySuggestion)
    }

    @Test
    fun `la entrada de actividad al editar tiene el tipo y los ids correctos`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(candidates, registerExpense, activity)
        val candidateId = candidates.save(pendingCandidate())

        val expenseId = editCandidate(candidateId, amount = 1_000L, merchant = "Comercio")

        val entry = activity.recent.value.first { it.type == ActivityType.CANDIDATE_EDITED }
        assertEquals(candidateId, entry.candidateId)
        assertEquals(expenseId, entry.expenseId)
    }

    // ----- Propagacion de campos del candidato al gasto -----

    @Test
    fun `editar con descripcion la propaga al gasto y la persiste en el candidato`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(candidates, registerExpense, activity)
        val candidateId = candidates.save(pendingCandidate())

        editCandidate(candidateId, amount = 1_000L, merchant = "Comercio", description = "Almuerzo con el equipo")

        assertEquals("Almuerzo con el equipo", expenses.expenses.value.first().description)
        assertEquals("Almuerzo con el equipo", candidates.findById(candidateId)?.description)
    }

    @Test
    fun `editar sin pasar descripcion queda con descripcion vacia por default`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(candidates, registerExpense, activity)
        val candidateId = candidates.save(pendingCandidate())

        editCandidate(candidateId, amount = 1_000L, merchant = "Comercio")

        assertEquals("", expenses.expenses.value.first().description)
    }

    @Test
    fun `condicion de carrera - dos ediciones concurrentes del mismo candidato NO duplican el gasto`() = runTest {
        // Regresion: EditCandidate protege con un Mutex el tramo lectura del
        // estado PENDING + escritura de EDITED (igual que ConfirmCandidate/
        // RejectCandidate/CreateCandidate). Sin ese Mutex, con una escritura
        // que tarda (delay simulando I/O real), dos ediciones concurrentes del
        // mismo candidato pasaban ambas el chequeo antes de que cualquiera
        // terminara de persistir, generando dos Expense para un solo candidato.
        // Si alguien saca el Mutex de EditCandidate, este test vuelve a fallar.
        val realCandidates = FakeCandidateRepository()
        val delayedCandidates = DelayedUpdateCandidateRepositoryForEdit(realCandidates)
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(delayedCandidates, registerExpense, activity)
        val candidateId = realCandidates.save(pendingCandidate())

        val first = async { editCandidate(candidateId, amount = 100L, merchant = "Edicion A") }
        val second = async { editCandidate(candidateId, amount = 200L, merchant = "Edicion B") }
        val results = listOfNotNull(first.await(), second.await())

        assertEquals(1, results.size)
        assertEquals(1, expenses.expenses.value.size)
    }
}
