package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditExpenseTest {

    @Test
    fun `edita monto, comercio y categoria de un gasto existente`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val editExpense = EditExpense(expenses, activity)

        val id = expenses.save(
            Expense(
                amount = 50_000,
                merchant = "Farmacia",
                categoryId = "other",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        editExpense(id, amount = 65_000, merchant = "Farmacia Central", categoryId = "health")

        val updated = expenses.findById(id)
        assertEquals(65_000L, updated?.amount)
        assertEquals("Farmacia Central", updated?.merchant)
        assertEquals("health", updated?.categoryId)
    }

    @Test
    fun `registra la edicion en Actividad`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val editExpense = EditExpense(expenses, activity)

        val id = expenses.save(
            Expense(
                amount = 50_000,
                merchant = "Farmacia",
                categoryId = "other",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        editExpense(id, amount = 65_000, merchant = "Farmacia Central", categoryId = "health")

        val entry = activity.recent.value.first()
        assertEquals(ActivityType.EXPENSE_EDITED, entry.type)
        assertEquals(id, entry.expenseId)
    }

    @Test
    fun `no hace nada si el gasto no existe`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val editExpense = EditExpense(expenses, activity)

        editExpense(999L, amount = 1_000, merchant = "Nada", categoryId = "other")

        assertNull(expenses.findById(999L))
        assertEquals(0, activity.recent.value.size)
    }

    @Test
    fun `no hace nada si el gasto no existe (id negativo o cero)`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val editExpense = EditExpense(expenses, activity)

        editExpense(0L, amount = 1_000, merchant = "Nada", categoryId = "other")
        editExpense(-5L, amount = 1_000, merchant = "Nada", categoryId = "other")

        assertEquals(0, expenses.expenses.value.size)
        assertEquals(0, activity.recent.value.size)
    }

    @Test
    fun `editar con los mismos valores igual persiste y registra actividad (no hay deteccion de no-op)`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val editExpense = EditExpense(expenses, activity)

        val id = expenses.save(
            Expense(
                amount = 50_000,
                merchant = "Farmacia",
                categoryId = "other",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        editExpense(id, amount = 50_000, merchant = "Farmacia", categoryId = "other")

        val updated = expenses.findById(id)
        assertEquals(50_000L, updated?.amount)
        assertEquals("Farmacia", updated?.merchant)
        assertEquals("other", updated?.categoryId)
        // No hay optimizacion de no-op: la edicion "idempotente" igual
        // genera una entrada de actividad.
        assertEquals(1, activity.recent.value.size)
        assertEquals(ActivityType.EXPENSE_EDITED, activity.recent.value.first().type)
    }

    @Test
    fun `actualiza la descripcion cuando se pasa un valor`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val editExpense = EditExpense(expenses, activity)

        val id = expenses.save(
            Expense(
                amount = 50_000,
                merchant = "Farmacia",
                categoryId = "other",
                description = "original",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        editExpense(id, amount = 50_000, merchant = "Farmacia", categoryId = "other", description = "nueva descripcion")

        assertEquals("nueva descripcion", expenses.findById(id)?.description)
    }

    @Test
    fun `mantiene la descripcion existente si no se pasa una nueva`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val editExpense = EditExpense(expenses, activity)

        val id = expenses.save(
            Expense(
                amount = 50_000,
                merchant = "Farmacia",
                categoryId = "other",
                description = "no la toques",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        editExpense(id, amount = 65_000, merchant = "Farmacia Central", categoryId = "health")

        assertEquals("no la toques", expenses.findById(id)?.description)
    }

    @Test
    fun `permite vaciar la descripcion pasando string vacio explicitamente`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val editExpense = EditExpense(expenses, activity)

        val id = expenses.save(
            Expense(
                amount = 50_000,
                merchant = "Farmacia",
                categoryId = "other",
                description = "algo",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        editExpense(id, amount = 50_000, merchant = "Farmacia", categoryId = "other", description = "")

        assertEquals("", expenses.findById(id)?.description)
    }

    @Test
    fun `permite editar categoryId a un valor invalido sin validar contra el enum Category`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val editExpense = EditExpense(expenses, activity)

        val id = expenses.save(
            Expense(
                amount = 50_000,
                merchant = "Farmacia",
                categoryId = "other",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        editExpense(id, amount = 50_000, merchant = "Farmacia", categoryId = "NO_EXISTE_ESTA_CATEGORIA")

        assertEquals("NO_EXISTE_ESTA_CATEGORIA", expenses.findById(id)?.categoryId)
    }
}
