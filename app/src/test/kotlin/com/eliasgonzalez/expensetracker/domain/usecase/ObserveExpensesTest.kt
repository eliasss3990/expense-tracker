package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ObserveExpensesTest {

    @Test
    fun `expone el mismo StateFlow que el repositorio, sin copiarlo`() {
        val expenses = FakeExpenseRepository()
        val observeExpenses = ObserveExpenses(expenses)

        assertSame(expenses.expenses, observeExpenses())
    }

    @Test
    fun `refleja cambios posteriores del repositorio (es reactivo, no una foto fija)`() = runTest {
        val expenses = FakeExpenseRepository()
        val observeExpenses = ObserveExpenses(expenses)

        assertEquals(0, observeExpenses().value.size)

        expenses.save(
            Expense(
                amount = 10_000,
                merchant = "Kiosco",
                occurredAt = 1L,
                createdAt = 1L,
                source = ExpenseSource.MANUAL,
            )
        )

        assertEquals(1, observeExpenses().value.size)
    }
}
