package com.eliasgonzalez.expensetracker.data.analysis

import com.eliasgonzalez.expensetracker.domain.analysis.CategoryOption
import com.eliasgonzalez.expensetracker.domain.chat.ChatPeriod
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON Schema que restringe la salida del modelo en el chat: UNA accion por respuesta (anyOf), y cada accion lleva
 * solo sus campos, todos obligatorios y sin nulos. En la bateria del telefono esta forma acerto 36/40 acciones contra
 * 31/40 del objeto unico con campos nulos. Ninguna accion puede apuntar a "todos los gastos".
 */
internal object ChatSchema {
    private const val MAX_TEXT = 80
    private const val MAX_DESCRIPTION = 120
    private const val MAX_REPLY = 160
    private const val MAX_LIMIT = 20
    const val ALL = "ALL"

    fun build(categories: List<CategoryOption>): String {
        val categoryIds = categories.map { it.id }
        return JSONObject().put(
            "anyOf",
            JSONArray(
                listOf(
                    action(
                        "QUERY",
                        "metric" to enumOf("SUM", "COUNT", "LIST", "AVERAGE"),
                        "period" to enumOf(*ChatPeriod.entries.map { it.name }.toTypedArray()),
                        "from" to optionalDate(), "to" to optionalDate(),
                        "category" to enumOf(ALL, *categoryIds.toTypedArray()),
                        "text" to text(),
                        "sort" to enumOf("RECENT", "AMOUNT"),
                        "limit" to JSONObject().put("type", "integer").put("minimum", 0).put("maximum", MAX_LIMIT),
                    ),
                    action(
                        "CREATE",
                        "amount" to JSONObject().put("type", "integer"),
                        "merchant" to text(),
                        "description" to longText(),
                        "category" to enumOf(*categoryIds.ifEmpty { listOf(ALL) }.toTypedArray()),
                        "date" to date(),
                    ),
                    action(
                        "EDIT", "reference" to reference(), "text" to text(),
                        "field" to enumOf("AMOUNT", "MERCHANT", "DESCRIPTION", "CATEGORY", "DATE"), "new_value" to text(),
                    ),
                    action("DELETE", "reference" to reference(), "text" to text()),
                    action("REVISE", "field" to enumOf("AMOUNT", "MERCHANT", "DESCRIPTION", "CATEGORY", "DATE"), "new_value" to text()),
                    action("CATEGORY_CREATE", "name" to text()),
                    action("CATEGORY_RENAME", "name" to text(), "new_name" to text()),
                    action("CATEGORY_DELETE", "name" to text()),
                    action("REVISE_NAME", "new_name" to text()),
                    action("CANCEL"),
                    action("HELP"),
                    action("SMALLTALK", "reply" to JSONObject().put("type", "string").put("maxLength", MAX_REPLY)),
                ),
            ),
        ).toString()
    }

    private fun action(name: String, vararg fields: Pair<String, JSONObject>): JSONObject {
        val props = JSONObject().put("action", enumOf(name))
        fields.forEach { (key, schema) -> props.put(key, schema) }
        return JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put("required", JSONArray(listOf("action") + fields.map { it.first }))
            .put("properties", props)
    }

    private fun enumOf(vararg values: String) = JSONObject().put("type", "string").put("enum", JSONArray(values.toList()))
    private fun reference() = enumOf("LAST", "MOST_EXPENSIVE", "BY_FILTER")
    /** La descripcion de un gasto es una frase, no una palabra: mas margen que un nombre. */
    private fun longText() = JSONObject().put("type", "string").put("maxLength", MAX_DESCRIPTION)
    private fun text() = JSONObject().put("type", "string").put("maxLength", MAX_TEXT)
    /** Vacio salvo con period CUSTOM: asi el modelo no inventa un rango cuando no lo hay. */
    private fun optionalDate() = JSONObject().put("type", "string").put("pattern", "^([0-9]{4}-[0-9]{2}-[0-9]{2})?$")
    private fun date() = JSONObject().put("type", "string").put("pattern", "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
}
