package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisEvent
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisEventKey
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisMode
import com.eliasgonzalez.expensetracker.domain.analysis.NotificationSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.ParserEvidence
import com.eliasgonzalez.expensetracker.domain.analysis.ParserSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.SourceTrust
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandleDetectedNotificationTest {

    private val candidates = FakeCandidateRepository()
    private val queue = FakeAnalysisQueue()
    private val announced = mutableListOf<ExpenseCandidate>()
    private var queuedSignals = 0
    private var mode = AnalysisMode.AUTOMATIC
    private var modelReady = true

    private fun handler() = HandleDetectedNotification(
        queue = queue,
        createCandidate = CreateCandidate(candidates, FakeActivityRepository()),
        findPendingCandidate = FindPendingCandidate(candidates),
        announcer = { announced += it },
        modeProvider = { mode },
        modelAvailability = { modelReady },
        onQueued = { queuedSignals++ },
    )

    private fun event(parser: ParserEvidence): AnalysisEvent {
        val n = NotificationSnapshot("py.banco", "Banco", "Aviso", "Compra por Gs. 85.000 en Tienda", "", 1_000L)
        return AnalysisEvent(AnalysisEventKey.of(n), n, parser, SourceTrust.BANK)
    }

    private val parsed = ParserEvidence.Parsed(ParserSnapshot(85_000, "PYG", "Tienda", true, "p", confidence = 0.6))

    @Test
    fun `sin modelo listo es el flujo de siempre y no se encola`() = runTest {
        modelReady = false
        handler()(event(parsed))
        assertEquals(1, candidates.candidates.value.size)
        assertEquals(1, announced.size)
        assertTrue(queue.rows.isEmpty())
    }

    @Test
    fun `en sombra es el flujo de siempre y ademas se encola para medir`() = runTest {
        mode = AnalysisMode.SHADOW
        handler()(event(parsed))
        assertEquals(1, candidates.candidates.value.size)
        assertEquals(1, queue.rows.size)
        assertEquals(1, queuedSignals)
    }

    @Test
    fun `en automatico con modelo listo solo se encola, el procesador decide`() = runTest {
        handler()(event(parsed))
        assertTrue(candidates.candidates.value.isEmpty())
        assertTrue(announced.isEmpty())
        assertEquals(1, queue.rows.size)
    }

    @Test
    fun `el mismo evento dos veces no se encola dos veces ni dispara dos veces`() = runTest {
        val h = handler()
        h(event(parsed)); h(event(parsed))
        assertEquals(1, queue.rows.size)
        assertEquals(1, queuedSignals)
    }

    @Test
    fun `sin parser y sin modelo no hay nada que crear ni se pierde nada que estuviera`() = runTest {
        modelReady = false
        handler()(event(ParserEvidence.NoParser))
        assertTrue(candidates.candidates.value.isEmpty())
    }

    @Test
    fun `el candidato del flujo de siempre conserva la confianza del parser`() = runTest {
        modelReady = false
        handler()(event(parsed))
        assertEquals(0.6, candidates.candidates.value.single().confidence, 0.0)
    }
}
