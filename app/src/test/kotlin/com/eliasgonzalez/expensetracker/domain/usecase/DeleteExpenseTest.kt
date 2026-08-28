package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteExpenseTest {

    @Test
    fun `borra un gasto existente`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val deleteExpense = DeleteExpense(expenses, activity)

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

        deleteExpense(id)

        assertNull(expenses.findById(id))
    }

    @Test
    fun `registra el borrado en Actividad con el resumen del gasto`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val deleteExpense = DeleteExpense(expenses, activity)

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

        deleteExpense(id)

        val entry = activity.recent.value.first()
        assertEquals(ActivityType.EXPENSE_DELETED, entry.type)
        assertEquals(id, entry.expenseId)
        assertTrue(entry.summary.contains("Farmacia"))
    }

    @Test
    fun `no hace nada ni registra actividad si el gasto no existe`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val deleteExpense = DeleteExpense(expenses, activity)

        deleteExpense(999L)

        assertEquals(0, activity.recent.value.size)
    }

    @Test
    fun `many borra todos los ids indicados`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val deleteExpense = DeleteExpense(expenses, activity)

        val id1 = expenses.save(
            Expense(
                amount = 10_000,
                merchant = "A",
                categoryId = "other",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )
        val id2 = expenses.save(
            Expense(
                amount = 20_000,
                merchant = "B",
                categoryId = "other",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )
        val id3 = expenses.save(
            Expense(
                amount = 30_000,
                merchant = "C",
                categoryId = "other",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        deleteExpense.many(listOf(id1, id3))

        assertNull(expenses.findById(id1))
        assertNull(expenses.findById(id3))
        assertEquals(id2, expenses.findById(id2)?.id)
        assertEquals(2, activity.recent.value.count { it.type == ActivityType.EXPENSE_DELETED })
    }
}
