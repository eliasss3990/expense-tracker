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
}
