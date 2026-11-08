package com.eliasgonzalez.expensetracker.data.local

import com.eliasgonzalez.expensetracker.data.analysis.InterpretationCodec
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisMode
import com.eliasgonzalez.expensetracker.domain.analysis.CategorySource
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionAudit
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionAuditRepository
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionEffect
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionReason
import com.eliasgonzalez.expensetracker.domain.analysis.RegistrationOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LocalDecisionAuditRepository(private val db: SqlDatabase) : DecisionAuditRepository {

    override suspend fun record(audit: DecisionAudit) {
        withContext(Dispatchers.IO) {
            db.insert(
                """INSERT INTO decision_audit (event_key, decided_at, mode, source_app, outcome, reasons, effect,
                    expense_id, candidate_id, interpretations, error_class, category_id, category_source)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                listOf(
                    audit.eventKey, audit.decidedAt, audit.mode.name, audit.sourceApp, audit.outcome?.name,
                    audit.reasons.joinToString(",") { it.name }, audit.effect.name, audit.expenseId, audit.candidateId,
                    InterpretationCodec.encode(audit.interpretations), audit.errorClass,
                    audit.categoryId, audit.categorySource?.name,
                ),
            )
        }
    }

    override suspend fun recent(limit: Int): List<DecisionAudit> = withContext(Dispatchers.IO) {
        db.query("SELECT * FROM decision_audit ORDER BY id DESC LIMIT ?", listOf(limit)) { row ->
            DecisionAudit(
                eventKey = row.string("event_key"),
                decidedAt = row.long("decided_at"),
                mode = enumOr(row.string("mode"), AnalysisMode.SHADOW),
                sourceApp = row.string("source_app"),
                outcome = row.stringOrNull("outcome")?.let { name -> RegistrationOutcome.entries.firstOrNull { it.name == name } },
                reasons = row.string("reasons").split(",").filter(String::isNotBlank)
                    .mapNotNull { name -> DecisionReason.entries.firstOrNull { it.name == name } }.toSet(),
                effect = enumOr(row.string("effect"), DecisionEffect.GAVE_UP),
                expenseId = row.longOrNull("expense_id"),
                candidateId = row.longOrNull("candidate_id"),
                // Una fila danada no puede romper la lectura de todas las demas (es lo que se consulta para saber por que).
                interpretations = runCatching { InterpretationCodec.decode(row.string("interpretations")) }.getOrDefault(emptyList()),
                errorClass = row.stringOrNull("error_class"),
                categoryId = row.stringOrNull("category_id"),
                categorySource = row.stringOrNull("category_source")?.let { n -> CategorySource.entries.firstOrNull { it.name == n } },
            )
        }
    }

    override suspend fun pruneBefore(timestamp: Long) {
        withContext(Dispatchers.IO) { db.update("DELETE FROM decision_audit WHERE decided_at < ?", listOf(timestamp)) }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
