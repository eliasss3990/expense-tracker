package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisDecision
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisMode
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisModeProvider
import com.eliasgonzalez.expensetracker.domain.analysis.AnalyzeTransaction
import com.eliasgonzalez.expensetracker.domain.analysis.DetectedMovement
import com.eliasgonzalez.expensetracker.domain.analysis.NotificationAnalysisQueueItem
import com.eliasgonzalez.expensetracker.domain.analysis.NotificationSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.ParserSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.ReconcileTransaction
import com.eliasgonzalez.expensetracker.domain.analysis.ReconciledTransaction
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionAnalysisInput
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionAnalysisResult
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionAnalyzer
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionExtraction
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.domain.notification.CandidateAlertPublisher
import com.eliasgonzalez.expensetracker.domain.repository.NotificationAnalysisQueueRepository
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProcessNotificationAnalysisQueueTest {

    private val notification = NotificationSnapshot(
        packageName = "com.google.android.gm",
        applicationName = "Gmail",
        title = "Compra aprobada",
        text = "Pagaste Gs. 85.000 en Supermercado Real",
        bigText = "",
        timestamp = 1_700_000_000_000,
    )

    private val parser = ParserSnapshot(
        amount = 85_000,
        currency = "PYG",
        merchant = "Supermercado Real",
        merchantConfident = true,
        parserId = "test-parser",
    )

    private fun input(parserResult: ParserSnapshot? = null) =
        TransactionAnalysisInput(notification, parserResult)

    private fun result(
        movement: DetectedMovement = DetectedMovement.EXPENSE,
        currencyEvidence: String? = null,
    ) = TransactionAnalysisResult(
        extraction = TransactionExtraction(
            movement = movement,
            amount = 85_000,
            currency = "PYG",
            merchant = "Supermercado Real",
            movementEvidence = if (movement == DetectedMovement.INCOME) "Recibiste" else "Compra aprobada",
            amountEvidence = "Gs. 85.000",
            merchantEvidence = "Supermercado Real",
            currencyEvidence = currencyEvidence,
        ),
        modelVersion = "fake-model",
    )

    private class FakeQueue : NotificationAnalysisQueueRepository {
        private val items = mutableListOf<NotificationAnalysisQueueItem>()
        private val statuses = mutableMapOf<Long, String>()
        var completed: Pair<TransactionAnalysisResult, ReconciledTransaction>? = null

        override suspend fun enqueue(input: TransactionAnalysisInput): Long? {
            if (items.any { it.input == input }) return null
            items += NotificationAnalysisQueueItem(1L, input, 0)
            statuses[1L] = PENDING
            return 1L
        }

        override suspend fun nextPending(): NotificationAnalysisQueueItem? =
            items.firstOrNull { statuses[it.id] == PENDING }

        override suspend fun markProcessing(id: Long): Boolean {
            if (statuses[id] != PENDING) return false
            statuses[id] = PROCESSING
            return true
        }

        override suspend fun markCompleted(
            id: Long,
            result: TransactionAnalysisResult,
            reconciled: ReconciledTransaction,
        ) {
            statuses[id] = COMPLETED
            completed = result to reconciled
        }

        override suspend fun markFallback(id: Long, errorType: String) {
            statuses[id] = FALLBACK
        }

        override suspend fun markFailed(id: Long, errorType: String) {
            statuses[id] = FAILED
        }

        override suspend fun recoverProcessing(): Int {
            statuses.entries.filter { it.value == PROCESSING }.forEach { it.setValue(PENDING) }
            return 0
        }

        override suspend fun retryFailed(): Int {
            statuses.entries.filter { it.value == FAILED }.forEach { it.setValue(PENDING) }
            return 0
        }

        override suspend fun pruneTerminal(beforeTimestamp: Long): Int = 0

        override suspend fun metrics() = com.eliasgonzalez.expensetracker.domain.analysis.AnalysisQueueMetrics(
            pending = statuses.values.count { it == PENDING }.toLong(),
            processing = statuses.values.count { it == PROCESSING }.toLong(),
            completed = statuses.values.count { it == COMPLETED }.toLong(),
            fallback = statuses.values.count { it == FALLBACK }.toLong(),
            failed = statuses.values.count { it == FAILED }.toLong(),
            autoRegisterSuggested = if (completed?.second?.decision == AnalysisDecision.AUTO_REGISTER) 1L else 0L,
            reviewSuggested = if (completed?.second?.decision == AnalysisDecision.REQUIRES_REVIEW) 1L else 0L,
            rejected = if (completed?.second?.decision == AnalysisDecision.REJECT) 1L else 0L,
        )

        fun status(id: Long = 1L): String? = statuses[id]

        private companion object {
            const val PENDING = "PENDING"
            const val PROCESSING = "PROCESSING"
            const val COMPLETED = "COMPLETED"
            const val FALLBACK = "FALLBACK"
            const val FAILED = "FAILED"
        }
    }

    private class FakePublisher : CandidateAlertPublisher {
        val published = mutableListOf<ExpenseCandidate>()
        override fun publish(candidate: ExpenseCandidate) {
            published += candidate
        }
    }

    private fun processor(
        queue: FakeQueue,
        analyzer: TransactionAnalyzer,
        candidates: FakeCandidateRepository,
        expenses: FakeExpenseRepository,
        activity: FakeActivityRepository,
        publisher: FakePublisher,
        scope: kotlinx.coroutines.CoroutineScope,
        mode: AnalysisMode = AnalysisMode.SHADOW,
    ): ProcessNotificationAnalysisQueue {
        val sharedMutex = Mutex()
        val registerExpense = RegisterExpense(expenses, activity)
        return ProcessNotificationAnalysisQueue(
            queue = queue,
            analyzeTransaction = AnalyzeTransaction(analyzer, ReconcileTransaction()),
            createCandidate = CreateCandidate(candidates, activity, sharedMutex),
            findPendingCandidate = FindPendingCandidate(candidates),
            registerAnalyzedExpense = RegisterAnalyzedExpense(
                candidates, expenses, registerExpense, sharedMutex,
            ),
            alertPublisher = publisher,
            modeProvider = AnalysisModeProvider { mode },
            appScope = scope,
        )
    }

    @Test
    fun `auto register escribe un gasto sin crear candidato`() = runTest {
        val queue = FakeQueue()
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val publisher = FakePublisher()
        val worker = processor(queue, TransactionAnalyzer { result() }, candidates, expenses, activity, publisher, this, AnalysisMode.AUTOMATIC)

        worker.enqueue(input(parser))
        advanceUntilIdle()

        assertEquals(1, expenses.expenses.value.size)
        assertEquals(0, candidates.candidates.value.size)
        assertEquals(0, publisher.published.size)
        assertEquals("COMPLETED", queue.status())
        assertEquals(AnalysisDecision.AUTO_REGISTER, queue.completed?.second?.decision)
    }

    @Test
    fun `modo shadow conserva el comportamiento legacy aunque IA recomiende auto registro`() = runTest {
        val queue = FakeQueue()
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val publisher = FakePublisher()
        val worker = processor(
            queue, TransactionAnalyzer { result() }, candidates, expenses, activity, publisher,
            this, AnalysisMode.SHADOW,
        )

        worker.enqueue(input(parser))
        advanceUntilIdle()

        assertEquals(0, expenses.expenses.value.size)
        assertEquals(1, candidates.candidates.value.size)
        assertEquals(1, publisher.published.size)
        assertEquals(AnalysisDecision.AUTO_REGISTER, queue.completed?.second?.decision)
    }

    @Test
    fun `modo automatico puede registrar sin parser si toda la evidencia es literal`() = runTest {
        val queue = FakeQueue()
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val publisher = FakePublisher()
        val worker = processor(
            queue,
            TransactionAnalyzer { result(currencyEvidence = "Gs.") },
            candidates,
            expenses,
            activity,
            publisher,
            this,
            AnalysisMode.AUTOMATIC,
        )

        worker.enqueue(input())
        advanceUntilIdle()

        assertEquals(1, expenses.expenses.value.size)
        assertEquals(0, candidates.candidates.value.size)
        assertEquals(0, publisher.published.size)
        assertEquals("COMPLETED", queue.status())
        assertEquals(AnalysisDecision.AUTO_REGISTER, queue.completed?.second?.decision)
    }

    @Test
    fun `extraccion sin parser pasa a revision y publica candidato`() = runTest {
        val queue = FakeQueue()
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val publisher = FakePublisher()
        val worker = processor(queue, TransactionAnalyzer { result() }, candidates, expenses, activity, publisher, this, AnalysisMode.ASSISTED)

        worker.enqueue(input())
        advanceUntilIdle()

        assertEquals(0, expenses.expenses.value.size)
        assertEquals(1, candidates.candidates.value.size)
        assertEquals(1, publisher.published.size)
        assertEquals("COMPLETED", queue.status())
        assertEquals(AnalysisDecision.REQUIRES_REVIEW, queue.completed?.second?.decision)
    }

    @Test
    fun `un ingreso con evidencia se rechaza y no crea candidato`() = runTest {
        val queue = FakeQueue()
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val publisher = FakePublisher()
        val incomeNotification = notification.copy(
            title = "Recibiste una transferencia",
            text = "Recibiste Gs. 85.000",
        )
        val input = TransactionAnalysisInput(incomeNotification, null)
        val worker = processor(
            queue,
            TransactionAnalyzer { result(DetectedMovement.INCOME) },
            candidates,
            expenses,
            activity,
            publisher,
            this,
            AnalysisMode.AUTOMATIC,
        )

        worker.enqueue(input)
        advanceUntilIdle()

        assertEquals(0, candidates.candidates.value.size)
        assertEquals(0, expenses.expenses.value.size)
        assertEquals("COMPLETED", queue.status())
        assertEquals(AnalysisDecision.REJECT, queue.completed?.second?.decision)
    }

    @Test
    fun `si el modelo falla se conserva el flujo legacy del parser`() = runTest {
        val queue = FakeQueue()
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val publisher = FakePublisher()
        val worker = processor(
            queue,
            TransactionAnalyzer { error("model not installed") },
            candidates,
            expenses,
            activity,
            publisher,
            this,
        )

        worker.enqueue(input(parser))
        advanceUntilIdle()

        assertEquals(1, candidates.candidates.value.size)
        assertEquals(1, publisher.published.size)
        assertEquals(0, expenses.expenses.value.size)
        assertEquals("FALLBACK", queue.status())
    }

    @Test
    fun `si el modelo falla sin parser se conserva el trabajo para reintento`() = runTest {
        val queue = FakeQueue()
        val candidates = FakeCandidateRepository()
        val expenses = FakeExpenseRepository()
        val activity = FakeActivityRepository()
        val publisher = FakePublisher()
        val worker = processor(
            queue,
            TransactionAnalyzer { error("model not installed") },
            candidates,
            expenses,
            activity,
            publisher,
            this,
        )

        worker.enqueue(input())
        advanceUntilIdle()

        assertEquals(0, candidates.candidates.value.size)
        assertEquals("FAILED", queue.status())
    }
}
