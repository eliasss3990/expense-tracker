package com.eliasgonzalez.expensetracker.data.analysis

import com.eliasgonzalez.expensetracker.domain.chat.ChatAmount
import com.eliasgonzalez.expensetracker.domain.chat.ChatIntent
import com.eliasgonzalez.expensetracker.domain.chat.ChatMetric
import com.eliasgonzalez.expensetracker.domain.chat.ChatPeriod
import com.eliasgonzalez.expensetracker.domain.chat.ChatSort
import com.eliasgonzalez.expensetracker.domain.chat.EditableField
import com.eliasgonzalez.expensetracker.domain.chat.ExpenseRef
import org.json.JSONObject
import java.time.LocalDate

/**
 * Traduce la salida del modelo a una [ChatIntent]. Tolerante con el formato (texto alrededor, cercas de codigo) y
 * estricto con el contenido: lo que no se entiende es [ChatIntent.Unreadable], nunca una accion adivinada.
 */
internal object ChatOutputParser {
    private const val MAX_LIMIT = 20

    fun parse(raw: String): ChatIntent {
        val json = extractObject(raw) ?: return ChatIntent.Unreadable("sin objeto JSON")
        return try {
            when (json.optString("action")) {
                "QUERY" -> ChatIntent.Query(
                    metric = enumOrNull<ChatMetric>(json, "metric") ?: return ChatIntent.Unreadable("metric"),
                    period = enumOrNull<ChatPeriod>(json, "period") ?: return ChatIntent.Unreadable("period"),
                    from = date(json, "from"), to = date(json, "to"),
                    categoryId = json.optString("category").takeIf { it.isNotBlank() && it != ChatSchema.ALL },
                    text = json.optString("text").trim(),
                    sort = enumOrNull<ChatSort>(json, "sort") ?: ChatSort.RECENT,
                    limit = json.optInt("limit", 0).coerceIn(0, MAX_LIMIT),
                )
                "DELETE" -> ChatIntent.Delete(ref(json) ?: return ChatIntent.Unreadable("reference"))
                "CREATE" -> ChatIntent.Create(
                    amount = amount(json) ?: return ChatIntent.Unreadable("amount"),
                    merchant = json.optString("merchant"),
                    description = json.optString("description"),
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
                "CATEGORY_CREATE" -> ChatIntent.CategoryCreate(name(json, "name") ?: return ChatIntent.Unreadable("name"))
                "CATEGORY_RENAME" -> ChatIntent.CategoryRename(
                    name(json, "name") ?: return ChatIntent.Unreadable("name"),
                    name(json, "new_name") ?: return ChatIntent.Unreadable("new_name"),
                )
                "CATEGORY_DELETE" -> ChatIntent.CategoryDelete(name(json, "name") ?: return ChatIntent.Unreadable("name"))
                "REVISE_NAME" -> ChatIntent.ReviseName(name(json, "new_name") ?: return ChatIntent.Unreadable("new_name"))
                "CANCEL" -> ChatIntent.Cancel
                "HELP" -> ChatIntent.Help
                "SMALLTALK" -> ChatIntent.SmallTalk(json.optString("reply"))
                else -> ChatIntent.Unreadable("accion desconocida")
            }
        } catch (e: org.json.JSONException) {
            ChatIntent.Unreadable("json invalido")
        } catch (e: DateUnreadable) {
            ChatIntent.Unreadable("fecha ilegible")
        }
    }

    private fun extractObject(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()
    }

    /** Un nombre de categoria: obligatorio, sin espacios sobrantes. */
    private fun name(json: JSONObject, key: String): String? = json.optString(key).trim().takeIf { it.isNotEmpty() }

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
            is String -> ChatAmount.parse(v)
            else -> null
        }
    }

    /** Vacio o ausente = no hay fecha. Presente pero ilegible = [DateUnreadable]: no se degrada a "hoy" ni a "sin limite". */
    private fun date(json: JSONObject, key: String): LocalDate? {
        val text = json.optString(key).trim()
        if (text.isEmpty()) return null
        return runCatching { LocalDate.parse(text) }.getOrElse { throw DateUnreadable }
    }

    private object DateUnreadable : RuntimeException("fecha ilegible") {
        private fun readResolve(): Any = DateUnreadable
    }

    private inline fun <reified E : Enum<E>> enumOrNull(json: JSONObject, key: String): E? =
        enumValues<E>().firstOrNull { it.name == json.optString(key) }
}
