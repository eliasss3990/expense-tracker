package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.analysis.AmountMention
import com.eliasgonzalez.expensetracker.domain.analysis.AmountRole
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisMode
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionAudit
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionEffect
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationPass
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationResult
import com.eliasgonzalez.expensetracker.domain.analysis.MoneyDirection
import com.eliasgonzalez.expensetracker.domain.analysis.OperationStatus
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionInterpretation
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoverDiscardedTest {
    private val audit = FakeDecisionAudit()
    private val candidates = FakeCandidateRepository()
    private val announced = mutableListOf<ExpenseCandidate>()
    private var announceFails = false

    private fun recover() = RecoverDiscarded(
        audit = audit,
        createCandidate = CreateCandidate(candidates, FakeActivityRepository()),
        findPendingCandidate = FindPendingCandidate(candidates),
        announcer = { if (announceFails) error("sin permiso") else announced += it },
        now = { 5_000_000L },
    )

    private fun interpretation(amounts: List<AmountMention>, merchant: String? = "Supermercado Real", category: String? = "GROCERIES") =
        TransactionInterpretation(MoneyDirection.INCOMING, OperationStatus.COMPLETED, amounts, merchant, emptySet(), category)

    private fun entry(
        key: String = "k1",
        effect: DecisionEffect = DecisionEffect.DISCARDED,
        results: List<InterpretationResult> = listOf(
            InterpretationResult(interpretation(listOf(AmountMention(85_000, AmountRole.OPERATION, "PYG"), AmountMention(1_200_000, AmountRole.BALANCE, "PYG"))), InterpretationPass.PRIMARY, "m", 1)
        ),
    ) = DecisionAudit(key, decidedAt = 1_000L, mode = AnalysisMode.AUTOMATIC, sourceApp = "Banco", outcome = null,
        reasons = emptySet(), effect = effect, expenseId = null, candidateId = null, interpretations = results)

    @Test
    fun `un descarte se recupera como candidato pendiente con el importe de la operacion y su categoria`() = runTest {
        audit.record(entry())
        val r = recover()("k1")

        val id = (r as RecoverResult.Recovered).candidateId
        val c = candidates.findById(id)!!
        assertEquals(85_000L, c.amount) // el de la operacion, no el saldo
        assertEquals("Supermercado Real", c.merchant)
        assertEquals("GROCERIES", c.categorySuggestion)
        assertEquals(CandidateStatus.PENDING, c.status)
        assertEquals("ai-recovered", c.parserId)
        assertEquals("el usuario recibe el aviso", 1, announced.size)
    }

    @Test
    fun `solo se recupera lo que de verdad se descarto`() = runTest {
        for (effect in DecisionEffect.entries.filter { it != DecisionEffect.DISCARDED }) {
            audit.entries.clear()
            audit.record(entry(effect = effect))
            assertEquals(effect.name, RecoverResult.NotRecoverable, recover()("k1"))
        }
        assertTrue(candidates.candidates.value.isEmpty())
    }

    @Test
    fun `una clave que no existe no se puede recuperar`() = runTest {
        audit.record(entry(key = "otra"))
        assertEquals(RecoverResult.NotRecoverable, recover()("k1"))
    }

    @Test
    fun `sin interpretacion o sin un importe positivo no hay de donde armar el candidato`() = runTest {
        audit.record(entry(key = "sin-interp", results = emptyList()))
        audit.record(entry(key = "sin-importe", results = listOf(InterpretationResult(interpretation(emptyList()), InterpretationPass.PRIMARY, "m", 1))))
        audit.record(entry(key = "cero", results = listOf(InterpretationResult(interpretation(listOf(AmountMention(0, AmountRole.OPERATION, "PYG"))), InterpretationPass.PRIMARY, "m", 1))))
        for (k in listOf("sin-interp", "sin-importe", "cero")) assertEquals(k, RecoverResult.NotRecoverable, recover()(k))
    }

    @Test
    fun `sin importe de operacion usa el primero que haya y sin comercio usa la app de origen`() = runTest {
        audit.record(entry(results = listOf(InterpretationResult(interpretation(listOf(AmountMention(40_000, AmountRole.OTHER, null)), merchant = null, category = null), InterpretationPass.PRIMARY, "m", 1))))
        val id = (recover()("k1") as RecoverResult.Recovered).candidateId
        val c = candidates.findById(id)!!
        assertEquals(40_000L, c.amount)
        assertEquals("PYG", c.currency)
        assertEquals("Banco", c.merchant)
        assertEquals("OTHER", c.categorySuggestion)
        assertTrue(!c.merchantConfident)
    }

    @Test
    fun `recuperar dos veces seguidas no duplica el candidato`() = runTest {
        audit.record(entry())
        assertTrue(recover()("k1") is RecoverResult.Recovered)
        assertEquals(RecoverResult.Duplicate, recover()("k1"))
        assertEquals(1, candidates.candidates.value.size)
    }

    @Test
    fun `si no se puede avisar igual queda el candidato en la Bandeja`() = runTest {
        audit.record(entry())
        announceFails = true
        assertTrue(recover()("k1") is RecoverResult.Recovered)
        assertEquals(1, candidates.candidates.value.size)
    }
}
