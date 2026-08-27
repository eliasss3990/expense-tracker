package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EditCandidateTest {

    @Test
    fun `edita monto y comercio, crea el gasto con los valores editados`() = runTest {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        val editCandidate = EditCandidate(candidates, registerExpense, activity)

        val candidateId = candidates.save(
            ExpenseCandidate(
                amount = 85_000,
                merchant = "MCDONALDS",
                occurredAt = 1L,
                detectedAt = 1L,
                sourceType = ExpenseSource.NOTIFICATION,
            )
        )

        editCandidate(candidateId, amount = 90_000, merchant = "McDonald's - Shopping")

        val expense = expenses.expenses.value.first()
        assertEquals(90_000, expense.amount)
        assertEquals("McDonald's - Shopping", expense.merchant)
        assertEquals(CandidateStatus.EDITED, candidates.findById(candidateId)?.status)
    }
}
