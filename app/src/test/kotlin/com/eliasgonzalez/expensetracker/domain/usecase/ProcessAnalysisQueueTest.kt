package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.analysis.AmountMention
import com.eliasgonzalez.expensetracker.domain.analysis.AmountRole
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisEvent
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisEventKey
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisMode
import com.eliasgonzalez.expensetracker.domain.analysis.CandidateAnnouncer
import com.eliasgonzalez.expensetracker.domain.analysis.DecideRegistration
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionEffect
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionReason
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationRequest
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationResult
import com.eliasgonzalez.expensetracker.domain.analysis.MoneyDirection
import com.eliasgonzalez.expensetracker.domain.analysis.NotificationSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.OperationStatus
import com.eliasgonzalez.expensetracker.domain.analysis.ParserEvidence
import com.eliasgonzalez.expensetracker.domain.analysis.ParserSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.SourceTrust
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionInterpretation
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionInterpreter
import com.eliasgonzalez.expensetracker.domain.log.AppLog
import com.eliasgonzalez.expensetracker.domain.log.LogLevel
import com.eliasgonzalez.expensetracker.domain.log.LogSink
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessAnalysisQueueTest {

    private val candidates = FakeCandidateRepository()
    private val expenses = FakeExpenseRepository()
    private val activity = FakeActivityRepository()
    private val queue = FakeAnalysisQueue()
    private val audit = FakeDecisionAudit()
    private val candidateMutex = Mutex()
    private val expenseMutex = Mutex()
    private val announced = mutableListOf<ExpenseCandidate>()
    private val announcer = CandidateAnnouncer { announced += it }
    private var mode = AnalysisMode.AUTOMATIC
    private var ready = true
    private val logs = mutableListOf<String>()

    @After
    fun restoreLog() = AppLog.install(LogSink.None)

    private fun interpreter(vararg script: () -> TransactionInterpretation) = object : TransactionInterpreter {
        var calls = 0
        override suspend fun interpret(request: InterpretationRequest): InterpretationResult {
            val interpretation = script[minOf(calls++, script.lastIndex)]()
            return InterpretationResult(interpretation, request.pass, "test-model", 7)
        }
    }

    private val purchase = TransactionInterpretation(
        MoneyDirection.OUTGOING, OperationStatus.COMPLETED,
        listOf(AmountMention(85_000, AmountRole.OPERATION, "PYG"), AmountMention(1_200_000, AmountRole.BALANCE, "PYG")),
        "Supermercado Real",
    )
    private val refund = purchase.copy(direction = MoneyDirection.REFUND)

    private fun event(
        text: String = "Compra aprobada por Gs. 85.000 en Supermercado Real. Saldo: Gs. 1.200.000",
        parser: ParserEvidence = ParserEvidence.NoParser,
        trust: SourceTrust = SourceTrust.BANK,
        timestamp: Long = 1_000_000L,
    ): AnalysisEvent {
        val n = NotificationSnapshot("py.banco", "Banco", "Aviso", text, "", timestamp)
        return AnalysisEvent(AnalysisEventKey.of(n), n, parser, trust)
    }

    private fun processor(
        interpreter: TransactionInterpreter,
        awaitReady: suspend () -> Boolean = { true },
        maxAttempts: Int = 3,
    ): ProcessAnalysisQueue {
        AppLog.install(object : LogSink {
            override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) { logs += message }
        })
        val register = RegisterExpense(expenses, activity, expenseMutex)
        return ProcessAnalysisQueue(
            queue = queue,
            interpreter = interpreter,
            decide = DecideRegistration(),
            createCandidate = CreateCandidate(candidates, activity, candidateMutex),
            findPendingCandidate = FindPendingCandidate(candidates),
            autoRegister = AutoRegisterExpense(candidates, expenses, register, candidateMutex, awaitReady),
            announcer = announcer,
            audit = audit,
            modeProvider = { mode },
            maxAttempts = maxAttempts,
        )
    }

    // --- efectos por modo ---

    @Test
    fun `automatico registra solo una compra clara y lo audita con su motivo`() = runTest {
        queue.enqueue(event())
        processor(interpreter({ purchase })).drain()

        assertEquals(1, expenses.expenses.value.size)
        assertEquals(85_000L, expenses.expenses.value.single().amount)
        assertTrue(announced.isEmpty())
        val a = audit.entries.single()
        assertEquals(DecisionEffect.AUTO_REGISTERED, a.effect)
        assertEquals(expenses.expenses.value.single().id, a.expenseId)
        assertEquals(2, a.interpretations.size)
        assertEquals(FakeAnalysisQueue.State.DONE, queue.rows.single().state)
    }

    @Test
    fun `automatico manda a candidato lo dudoso y lo audita con los motivos`() = runTest {
        queue.enqueue(event())
        val doubtful = purchase.copy(status = OperationStatus.PENDING)
        processor(interpreter({ doubtful })).drain()

        assertTrue(expenses.expenses.value.isEmpty())
        assertEquals(1, candidates.candidates.value.size)
        assertEquals(1, announced.size)
        val a = audit.entries.single()
        assertEquals(DecisionEffect.CANDIDATE_CREATED, a.effect)
        assertTrue(DecisionReason.NOT_COMPLETED in a.reasons)
        assertNotNull(a.candidateId)
    }

    @Test
    fun `un reembolso se descarta sin crear nada pero queda auditado`() = runTest {
        queue.enqueue(event("Se reembolso Gs. 85.000 de tu compra en Supermercado Real"))
        processor(interpreter({ refund })).drain()

        assertTrue(expenses.expenses.value.isEmpty())
        assertTrue(candidates.candidates.value.isEmpty())
        assertEquals(DecisionEffect.DISCARDED, audit.entries.single().effect)
    }

    @Test
    fun `asistido nunca registra solo ni siquiera lo seguro, va a candidato`() = runTest {
        mode = AnalysisMode.ASSISTED
        queue.enqueue(event())
        processor(interpreter({ purchase })).drain()

        assertTrue(expenses.expenses.value.isEmpty())
        assertEquals(1, candidates.candidates.value.size)
        assertEquals(DecisionEffect.CANDIDATE_CREATED, audit.entries.single().effect)
    }

    @Test
    fun `sombra mide y audita sin tocar nada`() = runTest {
        mode = AnalysisMode.SHADOW
        queue.enqueue(event())
        processor(interpreter({ purchase })).drain()

        assertTrue(expenses.expenses.value.isEmpty())
        assertTrue(candidates.candidates.value.isEmpty())
        val a = audit.entries.single()
        assertEquals(DecisionEffect.SHADOW_ONLY, a.effect)
        assertNotNull("en sombra igual queda la decision que habria tomado", a.outcome)
    }

    // --- no perder avisos ---

    @Test
    fun `si la base no cargo no se completa, no cuenta intento y se registra al reintentar`() = runTest {
        var ready = false
        queue.enqueue(event())
        val p = processor(interpreter({ purchase }), awaitReady = { ready })

        p.drain()
        val row = queue.rows.single()
        assertEquals(FakeAnalysisQueue.State.PENDING, row.state)
        assertEquals(0, row.attempts)
        assertTrue(audit.entries.isEmpty())
        assertTrue(expenses.expenses.value.isEmpty())

        ready = true
        p.drain()
        assertEquals(1, expenses.expenses.value.size)
        assertEquals(FakeAnalysisQueue.State.DONE, queue.rows.single().state)
    }

    @Test
    fun `si el modelo falla se reintenta y al agotar los intentos sale el candidato del parser`() = runTest {
        val parser = ParserEvidence.Parsed(ParserSnapshot(85_000, "PYG", "Supermercado Real", true, "p"))
        queue.enqueue(event(parser = parser))
        val failing = object : TransactionInterpreter {
            override suspend fun interpret(request: InterpretationRequest): InterpretationResult = error("modelo caido")
        }
        val p = processor(failing, maxAttempts = 3)

        repeat(3) { p.drain() }

        assertEquals(1, candidates.candidates.value.size)
        assertEquals(DecisionEffect.FALLBACK_LEGACY, audit.entries.single().effect)
        assertEquals("IllegalStateException", audit.entries.single().errorClass)
        assertEquals(FakeAnalysisQueue.State.GAVE_UP, queue.rows.single().state)
    }

    @Test
    fun `sin parser y con el modelo caido queda auditado como rendido y no se inventa un gasto`() = runTest {
        queue.enqueue(event())
        val failing = object : TransactionInterpreter {
            override suspend fun interpret(request: InterpretationRequest): InterpretationResult = error("x")
        }
        val p = processor(failing, maxAttempts = 2)
        repeat(2) { p.drain() }

        assertTrue(expenses.expenses.value.isEmpty())
        assertEquals(DecisionEffect.GAVE_UP, audit.entries.single().effect)
    }

    @Test
    fun `el mismo evento encolado dos veces se procesa una sola vez`() = runTest {
        val e = event()
        assertNotNull(queue.enqueue(e))
        assertNull(queue.enqueue(e))
        processor(interpreter({ purchase })).drain()
        assertEquals(1, expenses.expenses.value.size)
    }

    @Test
    fun `el segundo aviso de la misma compra por otro canal no duplica el gasto`() = runTest {
        queue.enqueue(event(text = "Compra aprobada por Gs. 85.000 en Supermercado Real", timestamp = 1_000_000L))
        queue.enqueue(event(text = "Pagaste Gs. 85.000 en Supermercado Real (comprobante)", timestamp = 1_020_000L))
        processor(interpreter({ purchase })).drain()

        assertEquals(1, expenses.expenses.value.size)
        assertEquals(listOf(DecisionEffect.AUTO_REGISTERED, DecisionEffect.DUPLICATE), audit.entries.map { it.effect })
    }

    @Test
    fun `un PROCESSING que quedo por la muerte del proceso se recupera y se registra una sola vez`() = runTest {
        queue.enqueue(event())
        queue.markProcessing(1L) // el proceso murio a mitad
        val p = processor(interpreter({ purchase }))
        p.recoverOnStartup()
        p.drain()
        p.drain()
        assertEquals(1, expenses.expenses.value.size)
    }

    // --- el log explica el por que sin datos financieros ---

    @Test
    fun `el log dice por que se decidio asi sin montos comercios ni texto`() = runTest {
        queue.enqueue(event())
        processor(interpreter({ purchase.copy(status = OperationStatus.PENDING) })).drain()

        val line = logs.single { "AiDecision" in it || "evento=" in it }
        assertTrue(line, "decision=REVIEW" in line && "NOT_COMPLETED" in line && "efecto=CANDIDATE_CREATED" in line)
        assertTrue(line, "modelo=test-model" in line)
        for (secret in listOf("85000", "85.000", "Supermercado", "1.200.000", "Compra aprobada", "Banco")) {
            assertFalse("el log no debe contener '$secret': $line", secret in line)
        }
    }
}
