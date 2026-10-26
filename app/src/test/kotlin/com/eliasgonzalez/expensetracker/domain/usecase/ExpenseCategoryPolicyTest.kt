package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.catalog.CatalogCategoryPolicy
import com.eliasgonzalez.expensetracker.domain.catalog.CatalogDefaults
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.Category
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regla: desactivar una categoria NO mueve nada a "Otro" - ni gastos, ni
 * candidatos pendientes que la sugieren, ni ediciones. Solo un id que ya
 * no existe cae en "Otro" (red de seguridad ante una carrera de borrado).
 */
class ExpenseCategoryPolicyTest {

    private val catalog = FakeCatalogRepository(CatalogDefaults.categories)
    private val expenses = FakeExpenseRepository()
    private val activity = FakeActivityRepository()
    private val candidates = FakeCandidateRepository()
    private val policy = CatalogCategoryPolicy(catalog)
    // Dos mutex distintos, como en AppContainer: ConfirmCandidate/EditCandidate
    // toman el de candidatos y llaman a RegisterExpense, que toma el de gastos.
    // Con el MISMO mutex se bloquearian solos (Mutex no es reentrante).
    private val expenseMutex = Mutex()
    private val candidateMutex = Mutex()
    private val register = RegisterExpense(expenses, activity, expenseMutex, policy)
    private val edit = EditExpense(expenses, activity, expenseMutex, policy)

    private fun gasto(categoryId: String) = Expense(
        amount = 1_000, merchant = "x", categoryId = categoryId,
        occurredAt = 1, createdAt = 1, source = ExpenseSource.MANUAL,
    )

    private fun candidato(categoryId: String) = ExpenseCandidate(
        amount = 5_000, merchant = "Shell", occurredAt = 1, detectedAt = 1,
        sourceType = ExpenseSource.NOTIFICATION, categorySuggestion = categoryId,
        status = CandidateStatus.PENDING,
    )

    private fun categoriaGuardada(id: Long) = expenses.findById(id)!!.categoryId

    private suspend fun desactivar(id: String) =
        catalog.save(catalog.find("category", id)!!.copy(active = false))

    @Test
    fun `registrar con una categoria activa la conserva`() = runTest {
        assertEquals(Category.FOOD.id, categoriaGuardada(register(gasto(Category.FOOD.id))))
    }

    @Test
    fun `registrar con una categoria desactivada la conserva`() = runTest {
        desactivar(Category.FUEL.id)

        assertEquals(Category.FUEL.id, categoriaGuardada(register(gasto(Category.FUEL.id))))
    }

    @Test
    fun `registrar con una categoria inexistente guarda Otro (carrera borrar vs gasto nuevo)`() = runTest {
        assertEquals(Category.OTHER.id, categoriaGuardada(register(gasto("ya-borrada"))))
    }

    @Test
    fun `confirmar un candidato pendiente cuya categoria se desactivo conserva esa categoria`() = runTest {
        val confirm = ConfirmCandidate(candidates, register, activity, candidateMutex)
        val id = candidates.save(candidato(Category.FUEL.id))
        desactivar(Category.FUEL.id)

        val expenseId = confirm(id)!!

        assertEquals(Category.FUEL.id, categoriaGuardada(expenseId))
    }

    @Test
    fun `editar un candidato sin tocar su categoria desactivada la conserva`() = runTest {
        val editCandidate = EditCandidate(candidates, register, activity, candidateMutex)
        val id = candidates.save(candidato(Category.HEALTH.id))
        desactivar(Category.HEALTH.id)

        val expenseId = editCandidate(id, 6_000, "Shell")!!

        assertEquals(Category.HEALTH.id, categoriaGuardada(expenseId))
    }

    @Test
    fun `editar el monto de un gasto de una categoria ya desactivada NO lo mueve de categoria`() = runTest {
        val id = register(gasto(Category.FUEL.id))
        desactivar(Category.FUEL.id)

        edit(id, amount = 9_999, merchant = "x", categoryId = Category.FUEL.id)

        assertEquals(Category.FUEL.id, categoriaGuardada(id))
        assertEquals(9_999L, expenses.findById(id)!!.amount)
    }

    @Test
    fun `editar y cambiar a una categoria inexistente guarda Otro`() = runTest {
        val id = register(gasto(Category.FOOD.id))

        edit(id, amount = 1_000, merchant = "x", categoryId = "no-existe")

        assertEquals(Category.OTHER.id, categoriaGuardada(id))
    }

    @Test
    fun `sin politica inyectada el comportamiento anterior no cambia`() = runTest {
        val sinPolitica = RegisterExpense(expenses, activity)

        assertEquals("cualquier-cosa", categoriaGuardada(sinPolitica(gasto("cualquier-cosa"))))
    }
}
