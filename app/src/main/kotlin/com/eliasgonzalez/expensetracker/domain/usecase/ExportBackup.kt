package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import com.eliasgonzalez.expensetracker.domain.repository.CatalogRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository

// v2: agrega la lista `catalog` (categorías propias, renombres y desactivadas) y
// `unknownCategoryIds` (ids referenciados que el catálogo ya no describe). Los campos
// nuevos son aditivos: un lector de v2 que ignore los que no conoce sigue funcionando.
private const val BACKUP_FORMAT_VERSION = 2

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
    private val catalog: CatalogRepository,
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
        sb.append("\n  ],\n")
        sb.append("  \"catalog\": [\n")
        sb.append(catalog.entries.value.joinToString(",\n") { it.toJson("    ") })
        sb.append("\n  ],\n")
        // Ids de categoria que los gastos o candidatos referencian pero que el catalogo ya
        // no describe (categoria eliminada). Un importador futuro sabe asi que ese id existio
        // y no tiene nombre ni color, en vez de encontrarse una referencia colgada sin aviso.
        sb.append("  \"unknownCategoryIds\": [")
        sb.append(unknownCategoryIds().joinToString(", ") { it.q() })
        sb.append("]\n")
        sb.append("}\n")
        return sb.toString()
    }

    private fun unknownCategoryIds(): List<String> {
        val described = catalog.entries.value
            .filter { it.kind == CatalogKind.CATEGORY }
            .map { it.id }
            .toSet()
        val referenced = expenses.expenses.value.map { it.categoryId } +
            candidates.candidates.value.map { it.categorySuggestion }
        return referenced.filter { it !in described }.distinct().sorted()
    }

    private fun Expense.toJson(indent: String) = """
        |$indent{"id": $id, "amount": $amount, "currency": ${currency.q()}, "merchant": ${merchant.q()}, "categoryId": ${categoryId.q()}, "description": ${description.q()}, "occurredAt": $occurredAt, "createdAt": $createdAt, "source": ${source.name.q()}, "sourceReference": ${sourceReference?.toString() ?: "null"}}
    """.trimMargin()

    private fun ExpenseCandidate.toJson(indent: String) = """
        |$indent{"id": $id, "amount": $amount, "currency": ${currency.q()}, "merchant": ${merchant.q()}, "categorySuggestion": ${categorySuggestion.q()}, "occurredAt": $occurredAt, "detectedAt": $detectedAt, "sourceType": ${sourceType.name.q()}, "sourceApp": ${sourceApp?.q() ?: "null"}, "parserId": ${parserId?.q() ?: "null"}, "confidence": $confidence, "status": ${status.name.q()}}
    """.trimMargin()

    private fun CatalogEntry.toJson(indent: String) = """
        |$indent{"kind": ${kind.q()}, "id": ${id.q()}, "label": ${label.q()}, "color": ${color.q()}, "active": $active, "sortOrder": $sortOrder, "builtIn": $builtIn, "protected": $protected}
    """.trimMargin()

    private fun ActivityEntry.toJson(indent: String) = """
        |$indent{"id": $id, "type": ${type.name.q()}, "expenseId": ${expenseId?.toString() ?: "null"}, "candidateId": ${candidateId?.toString() ?: "null"}, "timestamp": $timestamp, "summary": ${summary.q()}}
    """.trimMargin()

    /**
     * Escapa un string para JSON a mano (sin librería). Antes solo cubría
     * backslash/comilla/\n - cualquier otro caracter de control (\r, \t,
     * etc.) quedaba crudo dentro de las comillas y rompía el JSON
     * resultante. RFC 8259 exige escapar TODO U+0000-U+001F.
     */
    private fun String.q(): String {
        val escaped = buildString {
            for (char in this@q) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (char.code < 0x20) {
                        append("\\u%04x".format(char.code))
                    } else {
                        append(char)
                    }
                }
            }
        }
        return "\"$escaped\""
    }
}
