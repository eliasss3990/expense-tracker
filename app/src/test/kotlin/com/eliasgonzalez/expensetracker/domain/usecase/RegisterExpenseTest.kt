package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RegisterExpenseTest {

    @Test
    fun `guarda el gasto y registra actividad`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)

        val id = registerExpense(
            Expense(
                amount = 85_000,
                merchant = "McDonalds",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        assertEquals(1, expenses.expenses.value.size)
        assertEquals(id, expenses.expenses.value.first().id)
        assertEquals(1, activity.recent.value.size)
        assertEquals(ActivityType.EXPENSE_CREATED, activity.recent.value.first().type)
    }

    @Test
    fun `acepta monto cero`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)

        val id = registerExpense(
            Expense(
                amount = 0,
                merchant = "Regalo",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        assertEquals(0L, expenses.findById(id)?.amount)
    }

    @Test
    fun `acepta monto negativo sin validar (reembolsos)`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)

        val id = registerExpense(
            Expense(
                amount = -15_000,
                merchant = "Reembolso",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        assertEquals(-15_000L, expenses.findById(id)?.amount)
        assertTrueContains(activity.recent.value.first().summary, "Reembolso")
    }

    @Test
    fun `acepta Long MAX_VALUE como monto sin crashear`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)

        val id = registerExpense(
            Expense(
                amount = Long.MAX_VALUE,
                merchant = "Monto extremo",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        assertEquals(Long.MAX_VALUE, expenses.findById(id)?.amount)
    }

    @Test
    fun `acepta comercio vacio`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)

        val id = registerExpense(
            Expense(
                amount = 1_000,
                merchant = "",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        assertEquals("", expenses.findById(id)?.merchant)
    }

    @Test
    fun `acepta comercio muy largo`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val nombreLargo = "A".repeat(10_000)

        val id = registerExpense(
            Expense(
                amount = 1_000,
                merchant = nombreLargo,
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        assertEquals(nombreLargo, expenses.findById(id)?.merchant)
    }

    @Test
    fun `acepta comercio con caracteres unicode raros y emojis`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val nombreUnicode = "Ñoño's Café 🍔₲日本語𝕊"

        val id = registerExpense(
            Expense(
                amount = 1_000,
                merchant = nombreUnicode,
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        assertEquals(nombreUnicode, expenses.findById(id)?.merchant)
    }

    @Test
    fun `acepta categoryId invalido sin crashear ni forzar fallback`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)

        val id = registerExpense(
            Expense(
                amount = 1_000,
                merchant = "Comercio",
                categoryId = "CATEGORIA_QUE_NO_EXISTE",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        // El caso de uso no valida contra el enum Category: persiste el
        // valor crudo tal cual, sin crashear ni caer a OTHER.
        assertEquals("CATEGORIA_QUE_NO_EXISTE", expenses.findById(id)?.categoryId)
    }

    @Test
    fun `acepta timestamps en cero y negativos sin crashear`() = runTest {
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)

        val id = registerExpense(
            Expense(
                amount = 1_000,
                merchant = "Comercio",
                occurredAt = 0L,
                createdAt = -1L,
                source = ExpenseSource.MANUAL,
            )
        )

        val saved = expenses.findById(id)
        assertEquals(0L, saved?.occurredAt)
        assertEquals(-1L, saved?.createdAt)
        assertEquals(-1L, activity.recent.value.first().timestamp)
    }

    private fun assertTrueContains(text: String, fragment: String) {
        org.junit.Assert.assertTrue(text.contains(fragment))
    }
}
