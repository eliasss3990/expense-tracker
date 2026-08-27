package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmCandidateTest {

    private fun buildUseCase(): Triple<ConfirmCandidate, FakeCandidateRepository, FakeExpenseRepository> {
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val registerExpense = RegisterExpense(expenses, activity)
        return Triple(ConfirmCandidate(candidates, registerExpense, activity), candidates, expenses)
    }

    @Test
    fun `acepta un candidato pendiente y crea el gasto`() = runTest {
        val (confirmCandidate, candidates, expenses) = buildUseCase()
        val candidateId = candidates.save(
            ExpenseCandidate(
                amount = 85_000,
                merchant = "McDonalds",
                occurredAt = 1L,
                detectedAt = 1L,
                sourceType = ExpenseSource.NOTIFICATION,
            )
        )

        val expenseId = confirmCandidate(candidateId)

        assertEquals(1, expenses.expenses.value.size)
        assertEquals(expenseId, expenses.expenses.value.first().id)
        assertEquals(CandidateStatus.ACCEPTED, candidates.findById(candidateId)?.status)
    }

    @Test
    fun `es idempotente - aceptar dos veces no duplica el gasto`() = runTest {
        val (confirmCandidate, candidates, expenses) = buildUseCase()
        val candidateId = candidates.save(
            ExpenseCandidate(
                amount = 85_000,
                merchant = "McDonalds",
                occurredAt = 1L,
                detectedAt = 1L,
                sourceType = ExpenseSource.NOTIFICATION,
            )
        )

        confirmCandidate(candidateId)
        val secondResult = confirmCandidate(candidateId)

        assertNull(secondResult)
        assertEquals(1, expenses.expenses.value.size)
    }

    @Test
    fun `candidato inexistente no rompe nada`() = runTest {
        val (confirmCandidate, _, expenses) = buildUseCase()

        val result = confirmCandidate(999L)

        assertNull(result)
        assertEquals(0, expenses.expenses.value.size)
    }
}
