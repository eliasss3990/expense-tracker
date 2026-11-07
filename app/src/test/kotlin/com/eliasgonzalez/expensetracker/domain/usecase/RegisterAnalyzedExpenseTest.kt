package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisDecision
import com.eliasgonzalez.expensetracker.domain.analysis.DetectedMovement
import com.eliasgonzalez.expensetracker.domain.analysis.NotificationSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.ReconciledTransaction
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RegisterAnalyzedExpenseTest {

    private val notification = NotificationSnapshot(
        packageName = "com.google.android.gm",
        applicationName = "Gmail",
        title = "Compra aprobada",
        text = "Pagaste Gs. 85.000 en Supermercado Real",
        bigText = "",
        timestamp = 1_700_000_000_000,
    )

    private val transaction = ReconciledTransaction(
        decision = AnalysisDecision.AUTO_REGISTER,
        movement = DetectedMovement.EXPENSE,
        amount = 85_000,
        currency = "PYG",
        merchant = "Supermercado Real",
        occurredAt = notification.timestamp,
        reasons = emptySet(),
    )

    @Test
    fun `auto registra directo sin crear candidato y registra actividad`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val register = RegisterExpense(expenses, activity)
        val useCase = RegisterAnalyzedExpense(
            candidates, expenses, register, Mutex(), now = { 99L },
        )

        val id = useCase(notification, transaction)

        assertNotNull(id)
        assertEquals(1, expenses.expenses.value.size)
        assertEquals(0, candidates.candidates.value.size)
        assertEquals(ExpenseSource.NOTIFICATION, expenses.expenses.value.single().source)
        assertEquals(99L, expenses.expenses.value.single().createdAt)
        assertEquals(1, activity.recent.value.size)
    }

    @Test
    fun `el mismo evento no puede registrarse dos veces`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val useCase = RegisterAnalyzedExpense(
            candidates, expenses, RegisterExpense(expenses, activity), Mutex(), now = { 99L },
        )

        assertNotNull(useCase(notification, transaction))
        assertNull(useCase(notification, transaction))
        assertEquals(1, expenses.expenses.value.size)
    }

    @Test
    fun `un candidato reciente equivalente bloquea el auto registro duplicado`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        candidates.save(
            ExpenseCandidate(
                amount = 85_000,
                currency = "PYG",
                merchant = "Supermercado Real",
                occurredAt = notification.timestamp,
                detectedAt = notification.timestamp,
                sourceType = ExpenseSource.NOTIFICATION,
                status = CandidateStatus.PENDING,
            )
        )
        val useCase = RegisterAnalyzedExpense(
            candidates, expenses, RegisterExpense(expenses, activity), Mutex(),
        )

        assertNull(useCase(notification, transaction))
        assertEquals(0, expenses.expenses.value.size)
    }

    @Test
    fun `un candidato editado de la misma notificacion bloquea el auto registro original`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        candidates.save(
            ExpenseCandidate(
                amount = 50_000,
                currency = "PYG",
                merchant = "Supermercado Real",
                occurredAt = notification.timestamp,
                detectedAt = notification.timestamp,
                sourceType = ExpenseSource.NOTIFICATION,
                sourceApp = notification.applicationName,
                status = CandidateStatus.EDITED,
            )
        )
        val useCase = RegisterAnalyzedExpense(
            candidates, expenses, RegisterExpense(expenses, activity), Mutex(),
        )

        assertNull(useCase(notification, transaction))
        assertEquals(0, expenses.expenses.value.size)
    }

    @Test
    fun `un gasto manual reciente equivalente bloquea el auto registro`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        expenses.save(
            Expense(
                amount = 85_000,
                currency = "PYG",
                merchant = "Supermercado Real",
                occurredAt = notification.timestamp,
                createdAt = notification.timestamp,
                source = ExpenseSource.MANUAL,
                sourceReference = null,
            )
        )
        val useCase = RegisterAnalyzedExpense(
            candidates, expenses, RegisterExpense(expenses, activity), Mutex(),
        )

        assertNull(
            useCase(
                notification.copy(timestamp = notification.timestamp + 30_000),
                transaction.copy(occurredAt = notification.timestamp + 30_000),
            )
        )
        assertEquals(1, expenses.expenses.value.size)
    }

    @Test
    fun `solo auto register puede entrar en este caso de uso`() = runTest {
        val expenses = FakeExpenseRepository()
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val useCase = RegisterAnalyzedExpense(
            candidates, expenses, RegisterExpense(expenses, activity), Mutex(),
        )

        assertNull(
            useCase(
                notification,
                transaction.copy(decision = AnalysisDecision.REQUIRES_REVIEW),
            )
        )
        assertEquals(0, expenses.expenses.value.size)
    }
}
