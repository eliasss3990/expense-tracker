package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository

private const val BACKUP_FORMAT_VERSION = 1

/**
 * Backup/exportación como primer paso hacia sync real: mucho más simple
 * que sincronización bidireccional y da una vía de recuperación de datos
 * sin depender de ningún proveedor externo. JSON armado a mano (sin
 * librería) para no meter una dependencia de serialización solo por esto.
 */
class ExportBackup(
    private val expenses: ExpenseRepository,
    private val candidates: CandidateRepository,
    private val activity: ActivityRepository,
) {
    operator fun invoke(): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"formatVersion\": $BACKUP_FORMAT_VERSION,\n")
        sb.append("  \"exportedAt\": ${System.currentTimeMillis()},\n")
        sb.append("  \"expenses\": [\n")
        sb.append(expenses.expenses.value.joinToString(",\n") { it.toJson("    ") })
        sb.append("\n  ],\n")
        sb.append("  \"candidates\": [\n")
        sb.append(candidates.candidates.value.joinToString(",\n") { it.toJson("    ") })
        sb.append("\n  ],\n")
        sb.append("  \"activity\": [\n")
        sb.append(activity.recent.value.joinToString(",\n") { it.toJson("    ") })
        sb.append("\n  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    private fun Expense.toJson(indent: String) = """
        |$indent{"id": $id, "amount": $amount, "currency": ${currency.q()}, "merchant": ${merchant.q()}, "categoryId": ${categoryId.q()}, "description": ${description.q()}, "occurredAt": $occurredAt, "createdAt": $createdAt, "source": ${source.name.q()}, "sourceReference": ${sourceReference?.toString() ?: "null"}}
    """.trimMargin()

    private fun ExpenseCandidate.toJson(indent: String) = """
        |$indent{"id": $id, "amount": $amount, "currency": ${currency.q()}, "merchant": ${merchant.q()}, "categorySuggestion": ${categorySuggestion.q()}, "occurredAt": $occurredAt, "detectedAt": $detectedAt, "sourceType": ${sourceType.name.q()}, "sourceApp": ${sourceApp?.q() ?: "null"}, "parserId": ${parserId?.q() ?: "null"}, "confidence": $confidence, "status": ${status.name.q()}}
    """.trimMargin()

    private fun ActivityEntry.toJson(indent: String) = """
        |$indent{"id": $id, "type": ${type.name.q()}, "expenseId": ${expenseId?.toString() ?: "null"}, "candidateId": ${candidateId?.toString() ?: "null"}, "timestamp": $timestamp, "summary": ${summary.q()}}
    """.trimMargin()

    private fun String.q(): String {
        val escaped = this
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        return "\"$escaped\""
    }
}
