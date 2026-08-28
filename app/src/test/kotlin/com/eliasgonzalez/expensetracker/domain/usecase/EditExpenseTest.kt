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
}
