package com.eliasgonzalez.expensetracker.data.analysis

import com.eliasgonzalez.expensetracker.domain.analysis.AmountMention
import com.eliasgonzalez.expensetracker.domain.analysis.AmountRole
import com.eliasgonzalez.expensetracker.domain.analysis.ModelDoubt
import com.eliasgonzalez.expensetracker.domain.analysis.MoneyDirection
import com.eliasgonzalez.expensetracker.domain.analysis.OperationStatus
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionInterpretation
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** La salida del modelo no se pudo leer como la estructura pedida. Reintentable. */
class ModelOutputException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Convierte el texto que devuelve el modelo en una [TransactionInterpretation]. Un modelo chico
 * agrega charla, cercas de codigo o escribe "85.000" en vez de 85000: se tolera eso, pero ante
 * algo ilegible se lanza (no se inventa un valor por defecto "seguro").
 */
internal object ModelOutputParser {

    fun parse(raw: String): TransactionInterpretation {
        val json = try {
            JSONObject(extractJsonObject(raw))
        } catch (e: JSONException) {
            throw ModelOutputException("La salida del modelo no es JSON", e)
        }
        return TransactionInterpretation(
            direction = enumOr(json.optString("direction"), MoneyDirection.UNKNOWN),
            status = enumOr(json.optString("status"), OperationStatus.UNKNOWN),
            amounts = amounts(json.optJSONArray("amounts")),
            merchant = json.optString("merchant").trim().takeIf { it.isNotEmpty() && !it.equals("null", true) },
            doubts = (json.optJSONArray("doubts") ?: JSONArray()).let { arr ->
                (0 until arr.length()).map { enumOr(arr.optString(it), ModelDoubt.OTHER) }.toSet()
            },
        )
    }

    /** Primer objeto JSON balanceado del texto, ignorando lo que haya antes o despues. */
    internal fun extractJsonObject(raw: String): String {
        val start = raw.indexOf('{')
        if (start < 0) throw ModelOutputException("No hay un objeto JSON en la salida del modelo")
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val c = raw[i]
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> if (--depth == 0) return raw.substring(start, i + 1)
            }
        }
        throw ModelOutputException("El objeto JSON de la salida del modelo esta incompleto")
    }

    private fun amounts(array: JSONArray?): List<AmountMention> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val value = integerOrNull(o.opt("value")) ?: return@mapNotNull null
            AmountMention(
                value = value,
                role = enumOr(o.optString("role"), AmountRole.OTHER),
                currency = o.optString("currency").trim().uppercase().takeIf { it.length == 3 && it.all(Char::isLetter) },
            )
        }
    }

    /** 85000, 85000.0 o "85.000" -> 85000. Un decimal real ("85.5") no es un monto entero: se descarta. */
    private fun integerOrNull(value: Any?): Long? = when (value) {
        is Int -> value.toLong()
        is Long -> value
        is Number -> value.toDouble().takeIf { it == Math.rint(it) }?.toLong() // 85000.0 llega como Double o BigDecimal
        is String -> value.trim().filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
            .let { cleaned ->
                if (Regex("""[.,]\d{1,2}$""").containsMatchIn(cleaned)) null else cleaned.filter(Char::isDigit).toLongOrNull()
            }
        else -> null
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: fallback
}
