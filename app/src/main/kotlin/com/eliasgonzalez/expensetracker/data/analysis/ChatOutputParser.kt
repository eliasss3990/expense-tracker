package com.eliasgonzalez.expensetracker.data.analysis

import com.eliasgonzalez.expensetracker.domain.chat.ChatIntent
import com.eliasgonzalez.expensetracker.domain.chat.ChatMetric
import com.eliasgonzalez.expensetracker.domain.chat.EditableField
import com.eliasgonzalez.expensetracker.domain.chat.ExpenseRef
import org.json.JSONObject
import java.time.LocalDate

/**
 * Traduce la salida del modelo a una [ChatIntent]. Tolerante con el formato (texto alrededor, cercas de codigo) y
 * estricto con el contenido: lo que no se entiende es [ChatIntent.Unreadable], nunca una accion adivinada.
 */
internal object ChatOutputParser {

    fun parse(raw: String): ChatIntent {
        val json = extractObject(raw) ?: return ChatIntent.Unreadable("sin objeto JSON")
        return try {
            when (json.optString("action")) {
                "QUERY" -> ChatIntent.Query(
                    metric = enumOrNull<ChatMetric>(json, "metric") ?: return ChatIntent.Unreadable("metric"),
                    from = date(json, "from"), to = date(json, "to"),
                    categoryId = json.optString("category").takeIf { it.isNotBlank() && it != ChatSchema.ALL },
                )
                "DESCRIBE" -> ChatIntent.Describe(ref(json) ?: return ChatIntent.Unreadable("reference"))
                "DELETE" -> ChatIntent.Delete(ref(json) ?: return ChatIntent.Unreadable("reference"))
                "CREATE" -> ChatIntent.Create(
                    amount = amount(json) ?: return ChatIntent.Unreadable("amount"),
                    merchant = json.optString("merchant"),
                    categoryId = json.optString("category").takeIf { it.isNotBlank() && it != ChatSchema.ALL },
                    date = date(json, "date"),
                )
                "EDIT" -> ChatIntent.Edit(
                    ref = ref(json) ?: return ChatIntent.Unreadable("reference"),
                    field = enumOrNull<EditableField>(json, "field") ?: return ChatIntent.Unreadable("field"),
                    newValue = json.optString("new_value"),
                )
                "REVISE" -> ChatIntent.Revise(
                    field = enumOrNull<EditableField>(json, "field") ?: return ChatIntent.Unreadable("field"),
                    newValue = json.optString("new_value"),
                )
                "CANCEL" -> ChatIntent.Cancel
                "SMALLTALK" -> ChatIntent.SmallTalk(json.optString("reply"))
                else -> ChatIntent.Unreadable("accion desconocida")
            }
        } catch (e: org.json.JSONException) {
            ChatIntent.Unreadable("json invalido")
        }
    }

    private fun extractObject(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()
    }

    private fun ref(json: JSONObject): ExpenseRef? = when (json.optString("reference")) {
        "LAST" -> ExpenseRef.Last
        "MOST_EXPENSIVE" -> ExpenseRef.MostExpensive
        "BY_FILTER" -> json.optString("text").trim().takeIf { it.isNotEmpty() }?.let { ExpenseRef.ByText(it) }
        else -> null
    }

    private fun amount(json: JSONObject): Long? {
        val v = json.opt("amount")
        return when (v) {
            is Number -> v.toLong().takeIf { v.toDouble() == it.toDouble() }
            is String -> v.filter(Char::isDigit).toLongOrNull()
            else -> null
        }
    }

    private fun date(json: JSONObject, key: String): LocalDate? =
        runCatching { LocalDate.parse(json.optString(key).trim()) }.getOrNull()

    private inline fun <reified E : Enum<E>> enumOrNull(json: JSONObject, key: String): E? =
        enumValues<E>().firstOrNull { it.name == json.optString(key) }
}
