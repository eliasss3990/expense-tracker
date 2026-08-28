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

    @Test
    fun `borrar el mismo id dos veces es no-op la segunda vez sin crashear`() = runTest {
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
        deleteExpense(id)

        assertNull(expenses.findById(id))
        // Solo una entrada de actividad: la segunda vez no encuentra el
        // gasto y no registra nada.
        assertEquals(1, activity.recent.value.size)
    }

    @Test
    fun `many con lista vacia no hace nada`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val deleteExpense = DeleteExpense(expenses, activity)

        deleteExpense.many(emptyList())

        assertEquals(0, activity.recent.value.size)
    }

    @Test
    fun `many con ids duplicados borra una vez y no crashea en la repeticion`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val deleteExpense = DeleteExpense(expenses, activity)

        val id = expenses.save(
            Expense(
                amount = 10_000,
                merchant = "A",
                categoryId = "other",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        deleteExpense.many(listOf(id, id, id))

        assertNull(expenses.findById(id))
        assertEquals(1, activity.recent.value.count { it.type == ActivityType.EXPENSE_DELETED })
    }

    @Test
    fun `many con mezcla de ids validos e invalidos borra solo los validos sin crashear`() = runTest {
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

        deleteExpense.many(listOf(id1, 999_999L, id2, -1L, 0L))

        assertNull(expenses.findById(id1))
        assertNull(expenses.findById(id2))
        assertEquals(0, expenses.expenses.value.size)
        assertEquals(2, activity.recent.value.count { it.type == ActivityType.EXPENSE_DELETED })
    }

    @Test
    fun `many con una lista muy grande borra todos los gastos existentes sin crashear`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val deleteExpense = DeleteExpense(expenses, activity)

        val ids = (1..150).map { n ->
            expenses.save(
                Expense(
                    amount = n.toLong(),
                    merchant = "Comercio $n",
                    categoryId = "other",
                    occurredAt = 1L,
                    createdAt = 1L,
                    source = ExpenseSource.MANUAL,
                )
            )
        }
        // Mezclamos ids inexistentes en la lista grande tambien.
        val idsConInvalidos = ids + listOf(999_997L, 999_998L, 999_999L)

        deleteExpense.many(idsConInvalidos)

        assertEquals(0, expenses.expenses.value.size)
        assertEquals(150, activity.recent.value.count { it.type == ActivityType.EXPENSE_DELETED })
    }
}
