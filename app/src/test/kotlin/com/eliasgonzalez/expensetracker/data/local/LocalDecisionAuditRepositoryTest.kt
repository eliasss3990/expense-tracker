package com.eliasgonzalez.expensetracker.data.local

import com.eliasgonzalez.expensetracker.domain.analysis.AmountMention
import com.eliasgonzalez.expensetracker.domain.analysis.AmountRole
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisMode
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionAudit
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionEffect
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionReason
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationPass
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationResult
import com.eliasgonzalez.expensetracker.domain.analysis.ModelDoubt
import com.eliasgonzalez.expensetracker.domain.analysis.MoneyDirection
import com.eliasgonzalez.expensetracker.domain.analysis.OperationStatus
import com.eliasgonzalez.expensetracker.domain.analysis.RegistrationOutcome
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionInterpretation
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

class LocalDecisionAuditRepositoryTest {
    private lateinit var conn: Connection
    private lateinit var repo: LocalDecisionAuditRepository

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        DbSchema.create(JdbcSqlDatabase(conn), emptyList())
        repo = LocalDecisionAuditRepository(JdbcSqlDatabase(conn))
    }

    @After
    fun tearDown() = conn.close()

    private fun audit(key: String, at: Long, effect: DecisionEffect = DecisionEffect.CANDIDATE_CREATED) = DecisionAudit(
        eventKey = key, decidedAt = at, mode = AnalysisMode.AUTOMATIC, sourceApp = "Banco",
        outcome = RegistrationOutcome.REVIEW,
        reasons = setOf(DecisionReason.NOT_COMPLETED, DecisionReason.PASSES_DISAGREE),
        effect = effect, expenseId = null, candidateId = 7L,
        interpretations = listOf(
            InterpretationResult(
                TransactionInterpretation(
                    MoneyDirection.OUTGOING, OperationStatus.PENDING,
                    listOf(AmountMention(85_000, AmountRole.OPERATION, "PYG"), AmountMention(1_200_000, AmountRole.BALANCE, null)),
                    "Tienda", setOf(ModelDoubt.AMBIGUOUS_STATUS),
                ),
                InterpretationPass.PRIMARY, "gemma-test", 321,
            )
        ),
    )

    @Test
    fun `lo registrado se puede leer completo para explicar la decision`() = runTest {
        val original = audit("k1", 100L)
        repo.record(original)
        assertEquals(listOf(original), repo.recent(10))
    }

    @Test
    fun `recent devuelve lo mas nuevo primero y respeta el limite`() = runTest {
        repo.record(audit("a", 1)); repo.record(audit("b", 2)); repo.record(audit("c", 3))
        assertEquals(listOf("c", "b"), repo.recent(2).map { it.eventKey })
    }

    @Test
    fun `un registro sin interpretacion ni resultado tambien se guarda`() = runTest {
        val gaveUp = audit("x", 5).copy(outcome = null, reasons = emptySet(), interpretations = emptyList(),
            effect = DecisionEffect.GAVE_UP, candidateId = null, errorClass = "IllegalStateException")
        repo.record(gaveUp)
        assertEquals(gaveUp, repo.recent(1).single())
    }

    @Test
    fun `pruneBefore borra lo viejo`() = runTest {
        repo.record(audit("old", 1)); repo.record(audit("new", 100))
        repo.pruneBefore(50)
        assertEquals(listOf("new"), repo.recent(10).map { it.eventKey })
    }

    @Test
    fun `un codigo de motivo o de efecto que ya no existe no rompe la lectura`() = runTest {
        repo.record(audit("k", 1))
        conn.createStatement().use { it.executeUpdate("UPDATE decision_audit SET reasons = 'MOTIVO_VIEJO,NOT_COMPLETED', effect = 'EFECTO_VIEJO'") }
        val read = repo.recent(1).single()
        assertEquals(setOf(DecisionReason.NOT_COMPLETED), read.reasons)
        assertEquals(DecisionEffect.GAVE_UP, read.effect)
    }
}
