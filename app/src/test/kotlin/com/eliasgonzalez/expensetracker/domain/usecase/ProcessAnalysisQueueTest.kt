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
import com.eliasgonzalez.expensetracker.domain.catalog.CatalogDefaults
import com.eliasgonzalez.expensetracker.domain.analysis.CategorySource
import com.eliasgonzalez.expensetracker.domain.log.AppLog
import com.eliasgonzalez.expensetracker.domain.model.CategoryIds
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
    private val catalog = FakeCatalogRepository(CatalogDefaults.categories)
    private val audit = FakeDecisionAudit()
    private val candidateMutex = Mutex()
    private val expenseMutex = Mutex()
    private val announced = mutableListOf<ExpenseCandidate>()
    private val notices = mutableListOf<String>()
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
        scheduleRetry: (Long) -> Unit = {},
        auditRepo: com.eliasgonzalez.expensetracker.domain.analysis.DecisionAuditRepository = audit,
        queueRepo: com.eliasgonzalez.expensetracker.domain.analysis.AnalysisQueueRepository = queue,
        categoryResolver: ResolveCategory = ResolveCategory(expenses, catalog),
        modelReady: Boolean = true,
    ): ProcessAnalysisQueue {
        AppLog.install(object : LogSink {
            override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) { logs += message }
        })
        val register = RegisterExpense(expenses, activity, expenseMutex)
        return ProcessAnalysisQueue(
            queue = queueRepo,
            interpreter = interpreter,
            decide = DecideRegistration(),
            createCandidate = CreateCandidate(candidates, activity, candidateMutex),
            findPendingCandidate = FindPendingCandidate(candidates),
            autoRegister = AutoRegisterExpense(candidates, expenses, register, candidateMutex, awaitReady),
            resolveCategory = categoryResolver,
            announcer = announcer,
            noticeAnnouncer = { notices += it },
            modelAvailability = { modelReady },
            audit = auditRepo,
            modeProvider = { mode },
            maxAttempts = maxAttempts,
            scheduleRetry = scheduleRetry,
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
        queue.enqueue(event().copy(legacyHandled = true)) // el flujo de siempre ya creo el candidato al llegar
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

    // --- categoria ---

    private val withCategory = purchase.copy(categoryId = CategoryIds.GROCERIES)

    @Test
    fun `el gasto auto-registrado lleva la categoria del modelo y se audita la fuente`() = runTest {
        queue.enqueue(event())
        processor(interpreter({ withCategory })).drain()

        assertEquals(CategoryIds.GROCERIES, expenses.expenses.value.single().categoryId)
        val a = audit.entries.single()
        assertEquals(CategoryIds.GROCERIES, a.categoryId)
        assertEquals(CategorySource.MODEL, a.categorySource)
    }

    @Test
    fun `si el usuario ya categorizo ese comercio se respeta su historial sobre el modelo`() = runTest {
        expenses.save(com.eliasgonzalez.expensetracker.domain.model.Expense(
            amount = 1, merchant = "Supermercado Real", categoryId = CategoryIds.SHOPPING, occurredAt = 1, createdAt = 1,
            source = com.eliasgonzalez.expensetracker.domain.model.ExpenseSource.MANUAL,
        ))
        queue.enqueue(event())
        processor(interpreter({ withCategory })).drain()

        val registered = expenses.expenses.value.single { it.sourceReference != null }
        assertEquals(CategoryIds.SHOPPING, registered.categoryId)
        assertEquals(CategorySource.MEMORY, audit.entries.single().categorySource)
    }

    @Test
    fun `un candidato por duda tambien sugiere la categoria`() = runTest {
        queue.enqueue(event())
        processor(interpreter({ withCategory.copy(status = OperationStatus.PENDING) })).drain()

        assertEquals(CategoryIds.GROCERIES, candidates.candidates.value.single().categorySuggestion)
    }

    @Test
    fun `sin categoria del modelo ni historial queda en el comodin`() = runTest {
        queue.enqueue(event())
        processor(interpreter({ purchase })).drain()

        assertEquals(CategoryIds.OTHER, expenses.expenses.value.single().categoryId)
        assertEquals(CategorySource.DEFAULT, audit.entries.single().categorySource)
    }

    @Test
    fun `al modelo se le pasan las categorias activas del catalogo en ambas pasadas`() = runTest {
        queue.enqueue(event())
        val seen = mutableListOf<List<String>>()
        val spying = object : TransactionInterpreter {
            override suspend fun interpret(request: InterpretationRequest): InterpretationResult {
                seen += request.categories.map { it.id }
                return InterpretationResult(purchase, request.pass, "test-model", 1)
            }
        }
        processor(spying).drain()

        assertEquals(2, seen.size)
        assertTrue(seen.all { CategoryIds.FOOD in it && CategoryIds.OTHER !in it })
    }

    @Test
    fun `un descarte no lleva categoria`() = runTest {
        queue.enqueue(event("Se reembolso Gs. 85.000"))
        processor(interpreter({ refund.copy(categoryId = CategoryIds.FOOD) })).drain()
        assertNull(audit.entries.single().categoryId)
    }

    // --- regresion: hallazgos del code-review (cada uno con su mutacion verificada) ---

    private val parsedEvidence = ParserEvidence.Parsed(ParserSnapshot(85_000, "PYG", "Supermercado Real", true, "p", confidence = 0.6))

    private suspend fun diedDuringAnalysis(id: Long, times: Int) = repeat(times) {
        queue.markProcessing(id) // empieza el analisis...
        queue.recoverProcessing() // ...y el proceso muere: vuelve a PENDING con el intento ya contado
    }

    @Test
    fun `un aviso cuyo analisis mato el proceso varias veces no queda huerfano, sale el candidato del parser`() = runTest {
        val id = queue.enqueue(event(parser = parsedEvidence))!!
        diedDuringAnalysis(id, times = 3)
        assertEquals(FakeAnalysisQueue.State.PENDING, queue.state(id))

        processor(interpreter({ purchase }), maxAttempts = 3).drain()

        assertEquals(FakeAnalysisQueue.State.GAVE_UP, queue.state(id))
        assertEquals(1, candidates.candidates.value.size)
        assertEquals(DecisionEffect.FALLBACK_LEGACY, audit.entries.single().effect)
        assertEquals("AttemptsExhausted", audit.entries.single().errorClass)
        assertEquals("el respaldo conserva la confianza del parser", 0.6, candidates.candidates.value.single().confidence, 0.0)
    }

    @Test
    fun `el respaldo tras fallar el modelo tambien conserva la confianza del parser`() = runTest {
        queue.enqueue(event(parser = parsedEvidence))
        val failing = object : TransactionInterpreter {
            override suspend fun interpret(request: InterpretationRequest): InterpretationResult = error("x")
        }
        val p = processor(failing, maxAttempts = 2)
        repeat(2) { p.drain() }
        assertEquals(0.6, candidates.candidates.value.single().confidence, 0.0)
    }

    @Test
    fun `un candidato por duda lleva la confianza del parser y sin parser lleva cero`() = runTest {
        val doubtful = purchase.copy(status = OperationStatus.PENDING)
        queue.enqueue(event(parser = parsedEvidence, timestamp = 1_000_000L))
        queue.enqueue(event(text = "Pago por Gs. 12.000 en Kiosco Norte", timestamp = 9_000_000L))
        val ofKiosk = doubtful.copy(amounts = listOf(AmountMention(12_000, AmountRole.OPERATION, "PYG")), merchant = "Kiosco Norte")
        var n = 0
        processor(interpreter({ if (n++ < 2) doubtful else ofKiosk })).drain()
        val byAmount = candidates.candidates.value.associateBy { it.amount }
        assertEquals(0.6, byAmount.getValue(85_000).confidence, 0.0)
        assertEquals(0.0, byAmount.getValue(12_000).confidence, 0.0)
    }

    @Test
    fun `sin parser un aviso agotado queda auditado como rendido y no desaparece sin rastro`() = runTest {
        val id = queue.enqueue(event())!!
        diedDuringAnalysis(id, times = 3)
        processor(interpreter({ purchase }), maxAttempts = 3).drain()
        assertEquals(FakeAnalysisQueue.State.GAVE_UP, queue.state(id))
        assertEquals(DecisionEffect.GAVE_UP, audit.entries.single().effect)
    }

    @Test
    fun `un efecto que falla en todos los intentos tampoco deja el aviso huerfano`() = runTest {
        queue.enqueue(event(parser = parsedEvidence))
        val real = candidates // dentro del object, "candidates" seria la propiedad del propio repositorio
        val failingCandidates = object : com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository by real {
            override suspend fun save(candidate: com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate): Long {
                if (failSaves) error("disco lleno")
                return real.save(candidate)
            }
        }
        val p = ProcessAnalysisQueue(
            queue = queue, interpreter = interpreter({ purchase.copy(status = OperationStatus.PENDING) }), decide = DecideRegistration(),
            createCandidate = CreateCandidate(failingCandidates, activity, candidateMutex),
            findPendingCandidate = FindPendingCandidate(candidates),
            autoRegister = AutoRegisterExpense(candidates, expenses, RegisterExpense(expenses, activity, expenseMutex), candidateMutex),
            resolveCategory = ResolveCategory(expenses, catalog), announcer = announcer, audit = audit,
            modeProvider = { mode }, maxAttempts = 3,
        )
        failSaves = true
        repeat(3) { p.drain() }
        failSaves = false
        p.drain() // el drain siguiente cierra lo agotado

        assertEquals(FakeAnalysisQueue.State.GAVE_UP, queue.state(1))
        assertEquals("el respaldo debia crear el candidato del parser", 1, candidates.candidates.value.size)
    }

    private var failSaves = false

    @Test
    fun `tras una falla se programa un reintento con espera creciente y tope`() = runTest {
        val delays = mutableListOf<Long>()
        val failing = object : TransactionInterpreter {
            override suspend fun interpret(request: InterpretationRequest): InterpretationResult = error("x")
        }
        queue.enqueue(event(parser = parsedEvidence))
        val p = processor(failing, maxAttempts = 10, scheduleRetry = { delays += it })
        repeat(4) { p.drain() }
        assertEquals(listOf(60_000L, 120_000L, 240_000L, 480_000L), delays)
        repeat(3) { p.drain() }
        assertEquals("la espera tiene tope de 15 minutos", 15 * 60_000L, delays.last())
    }

    @Test
    fun `si todo salio bien no se programa ningun reintento`() = runTest {
        var scheduled = false
        queue.enqueue(event())
        processor(interpreter({ purchase }), scheduleRetry = { scheduled = true }).drain()
        assertFalse(scheduled)
    }

    @Test
    fun `una base sin cargar tambien programa el reintento`() = runTest {
        val delays = mutableListOf<Long>()
        queue.enqueue(event())
        processor(interpreter({ purchase }), awaitReady = { false }, scheduleRetry = { delays += it }).drain()
        assertEquals(listOf(60_000L), delays)
    }

    @Test
    fun `una falla al auditar no tumba el drain ni deshace el gasto ya registrado`() = runTest {
        queue.enqueue(event())
        val brokenAudit = object : com.eliasgonzalez.expensetracker.domain.analysis.DecisionAuditRepository {
            override suspend fun record(audit: com.eliasgonzalez.expensetracker.domain.analysis.DecisionAudit) = error("disco lleno")
            override suspend fun recent(limit: Int) = emptyList<com.eliasgonzalez.expensetracker.domain.analysis.DecisionAudit>()
            override suspend fun pruneBefore(timestamp: Long) = Unit
        }
        processor(interpreter({ purchase }), auditRepo = brokenAudit).drain() // no debe lanzar

        assertEquals(1, expenses.expenses.value.size)
        assertEquals(FakeAnalysisQueue.State.DONE, queue.state(1))
    }

    @Test
    fun `una falla inesperada fuera de las zonas protegidas no sale de drain y el aviso se reintenta`() = runTest {
        queue.enqueue(event())
        var calls = 0
        val flakyResolver = ResolveCategory(expenses, catalog) { if (++calls == 2) error("base bloqueada") else true }
        val p = processor(interpreter({ purchase }), categoryResolver = flakyResolver)
        p.drain() // no debe lanzar

        assertEquals(FakeAnalysisQueue.State.PENDING, queue.state(1))
        assertTrue(expenses.expenses.value.isEmpty())
        p.drain()
        assertEquals(1, expenses.expenses.value.size)
    }

    // --- regresion: segundo review (reproducciones del agente) ---

    @Test
    fun `un aviso encolado en automatico no se pierde si el usuario pasa a sombra antes de procesarlo`() = runTest {
        // En Asistido/Automatico el aviso se encola SIN candidato de siempre: el analisis es su unica via.
        queue.enqueue(event(parser = parsedEvidence)) // legacyHandled = false
        mode = AnalysisMode.SHADOW // el usuario cambia de modo antes del drain
        processor(interpreter({ purchase })).drain()

        assertEquals("el aviso debia convertirse en candidato", 1, candidates.candidates.value.size)
        assertEquals(DecisionEffect.CANDIDATE_CREATED, audit.entries.single().effect)
    }

    @Test
    fun `lo agotado tampoco se pierde si el modo paso a sombra`() = runTest {
        val id = queue.enqueue(event(parser = parsedEvidence))!!
        diedDuringAnalysis(id, 3)
        mode = AnalysisMode.SHADOW
        processor(interpreter({ purchase }), maxAttempts = 3).drain()
        assertEquals(1, candidates.candidates.value.size)
    }

    @Test
    fun `un aviso sin importe utilizable le llega al usuario y no solo a la auditoria`() = runTest {
        queue.enqueue(event())
        val twoOperations = purchase.copy(amounts = listOf(AmountMention(85_000, AmountRole.OPERATION, "PYG"), AmountMention(90_000, AmountRole.OPERATION, "PYG")))
        processor(interpreter({ twoOperations })).drain()

        assertTrue(candidates.candidates.value.isEmpty())
        assertEquals(DecisionEffect.UNUSABLE, audit.entries.single().effect)
        assertEquals("el usuario debia ser avisado", listOf("Banco"), notices)
    }

    @Test
    fun `un correo sin importe legible no molesta al usuario ni deja candidato`() = runTest {
        queue.enqueue(event(trust = SourceTrust.MAILBOX))
        val twoOperations = purchase.copy(amounts = listOf(AmountMention(85_000, AmountRole.OPERATION, "PYG"), AmountMention(90_000, AmountRole.OPERATION, "PYG")))
        processor(interpreter({ twoOperations })).drain()

        assertTrue(candidates.candidates.value.isEmpty())
        assertEquals(DecisionEffect.IGNORED_UNREADABLE, audit.entries.single().effect)
        assertTrue("de un buzon no se avisa", notices.isEmpty())
    }

    @Test
    fun `un fallo al avisar de un aviso ilegible no rompe el procesamiento`() = runTest {
        queue.enqueue(event())
        val none = purchase.copy(amounts = emptyList())
        val p = ProcessAnalysisQueue(
            queue = queue, interpreter = interpreter({ none }), decide = DecideRegistration(),
            createCandidate = CreateCandidate(candidates, activity, candidateMutex),
            findPendingCandidate = FindPendingCandidate(candidates),
            autoRegister = AutoRegisterExpense(candidates, expenses, RegisterExpense(expenses, activity, expenseMutex), candidateMutex),
            resolveCategory = ResolveCategory(expenses, catalog), announcer = announcer,
            noticeAnnouncer = { error("sin permiso de notificaciones") }, audit = audit, modeProvider = { mode },
        )
        p.drain() // no debe lanzar
        assertEquals(FakeAnalysisQueue.State.DONE, queue.state(1))
    }

    @Test
    fun `un item que quedo PROCESSING por un drain abortado se retoma en el siguiente`() = runTest {
        val id = queue.enqueue(event())!!
        queue.markProcessing(id) // un drain anterior murio (cancelacion/excepcion) a mitad
        assertEquals(FakeAnalysisQueue.State.PROCESSING, queue.state(id))

        processor(interpreter({ purchase })).drain()

        assertEquals(FakeAnalysisQueue.State.DONE, queue.state(id))
        assertEquals(1, expenses.expenses.value.size)
    }

    @Test
    fun `si el modelo ya no esta los avisos encolados salen por el flujo de siempre y no quedan pendientes`() = runTest {
        queue.enqueue(event(parser = parsedEvidence, timestamp = 1_000_000L))
        queue.enqueue(event(text = "Aviso sin parser", timestamp = 9_000_000L))

        processor(interpreter({ purchase }), modelReady = false).drain()

        assertTrue("ninguno debe quedar pendiente", queue.rows.none { it.state == FakeAnalysisQueue.State.PENDING })
        assertEquals("el que tenia parser sale como candidato", 1, candidates.candidates.value.size)
        assertEquals(listOf(DecisionEffect.FALLBACK_LEGACY, DecisionEffect.GAVE_UP), audit.entries.map { it.effect })
        assertTrue("sin modelo no se registra nada solo", expenses.expenses.value.isEmpty())
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
