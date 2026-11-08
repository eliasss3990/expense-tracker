package com.eliasgonzalez.expensetracker.data.analysis

import com.eliasgonzalez.expensetracker.domain.analysis.AmountRole
import com.eliasgonzalez.expensetracker.domain.analysis.CategoryOption
import com.eliasgonzalez.expensetracker.domain.analysis.ModelDoubt
import com.eliasgonzalez.expensetracker.domain.analysis.MoneyDirection
import com.eliasgonzalez.expensetracker.domain.analysis.OperationStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON Schema de la respuesta, para la decodificacion restringida del runtime: el modelo SOLO puede
 * emitir un objeto que lo cumpla, asi que no puede devolver JSON roto, un enum inexistente ni una
 * categoria que no este en el catalogo del usuario. Los valores salen de los enums del dominio (un test
 * lo verifica) para que el esquema y el codigo no se desincronicen.
 *
 * Solo se usan construcciones basicas (enum, anyOf, required, additionalProperties) por compatibilidad
 * con el motor de gramaticas del runtime. El parser sigue siendo tolerante igualmente.
 */
internal object InterpretationSchema {
    private const val MAX_AMOUNTS = 8
    private const val MAX_MERCHANT_CHARS = 80
    private const val MAX_DOUBTS = 5

    fun build(categories: List<CategoryOption>): String = JSONObject().apply {
        put("type", "object")
        put("additionalProperties", false)
        put("required", JSONArray(listOf("direction", "status", "amounts", "merchant", "category", "doubts")))
        put(
            "properties",
            JSONObject().apply {
                put("direction", enumOf(MoneyDirection.entries.map { it.name }))
                put("status", enumOf(OperationStatus.entries.map { it.name }))
                put(
                    "amounts",
                    JSONObject().apply {
                        put("type", "array")
                        put("maxItems", MAX_AMOUNTS)
                        put("items", amountItem())
                    },
                )
                put("merchant", nullable(JSONObject().put("type", "string").put("maxLength", MAX_MERCHANT_CHARS)))
                put("category", nullable(enumOf(categories.map { it.id }), emptyAllowed = categories.isEmpty()))
                put(
                    "doubts",
                    JSONObject().apply {
                        put("type", "array")
                        put("maxItems", MAX_DOUBTS)
                        put("items", enumOf(ModelDoubt.entries.map { it.name }))
                    },
                )
            },
        )
    }.toString()

    private fun amountItem() = JSONObject().apply {
        put("type", "object")
        put("additionalProperties", false)
        put("required", JSONArray(listOf("value", "role", "currency")))
        put(
            "properties",
            JSONObject().apply {
                put("value", JSONObject().put("type", "integer").put("minimum", 0))
                put("role", enumOf(AmountRole.entries.map { it.name }))
                put("currency", nullable(JSONObject().put("type", "string").put("minLength", 3).put("maxLength", 3)))
            },
        )
    }

    private fun enumOf(values: List<String>) = JSONObject().put("enum", JSONArray(values))

    /** Nullable via anyOf (mas portable que `type: [..., "null"]`). Sin valores posibles, solo null. */
    private fun nullable(schema: JSONObject, emptyAllowed: Boolean = false): JSONObject =
        if (emptyAllowed) JSONObject().put("type", "null")
        else JSONObject().put("anyOf", JSONArray().put(schema).put(JSONObject().put("type", "null")))
}
