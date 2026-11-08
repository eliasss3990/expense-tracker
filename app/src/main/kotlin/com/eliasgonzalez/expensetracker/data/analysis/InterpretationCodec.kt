package com.eliasgonzalez.expensetracker.data.analysis

import com.eliasgonzalez.expensetracker.domain.analysis.AmountMention
import com.eliasgonzalez.expensetracker.domain.analysis.AmountRole
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationPass
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationResult
import com.eliasgonzalez.expensetracker.domain.analysis.ModelDoubt
import com.eliasgonzalez.expensetracker.domain.analysis.MoneyDirection
import com.eliasgonzalez.expensetracker.domain.analysis.OperationStatus
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionInterpretation
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializa las interpretaciones para la auditoria, en un JSON versionado ("v"): se puede leer
 * un registro viejo aunque el codigo cambie, y un valor desconocido de un enum no rompe la lectura
 * (cae a UNKNOWN/OTHER), asi la auditoria sigue sirviendo meses despues.
 */
internal object InterpretationCodec {
    private const val VERSION = 1

    fun encode(results: List<InterpretationResult>): String =
        JSONArray().apply { results.forEach { put(encode(it)) } }.toString()

    fun decode(json: String): List<InterpretationResult> {
        val array = JSONArray(json)
        return (0 until array.length()).map { decode(array.getJSONObject(it)) }
    }

    private fun encode(r: InterpretationResult) = JSONObject().apply {
        put("v", VERSION)
        put("pass", r.pass.name)
        put("modelId", r.modelId)
        put("durationMs", r.durationMillis)
        put("direction", r.interpretation.direction.name)
        put("status", r.interpretation.status.name)
        put("merchant", r.interpretation.merchant ?: JSONObject.NULL)
        put("category", r.interpretation.categoryId ?: JSONObject.NULL)
        put("doubts", JSONArray(r.interpretation.doubts.map { it.name }))
        put(
            "amounts",
            JSONArray().apply {
                r.interpretation.amounts.forEach {
                    put(JSONObject().put("value", it.value).put("role", it.role.name).put("currency", it.currency ?: JSONObject.NULL))
                }
            },
        )
    }

    private fun decode(o: JSONObject): InterpretationResult {
        val amounts = o.optJSONArray("amounts") ?: JSONArray()
        val doubts = o.optJSONArray("doubts") ?: JSONArray()
        return InterpretationResult(
            interpretation = TransactionInterpretation(
                direction = enumOr(o.optString("direction"), MoneyDirection.UNKNOWN),
                status = enumOr(o.optString("status"), OperationStatus.UNKNOWN),
                amounts = (0 until amounts.length()).map {
                    val a = amounts.getJSONObject(it)
                    AmountMention(
                        a.getLong("value"),
                        enumOr(a.optString("role"), AmountRole.OTHER),
                        if (a.isNull("currency")) null else a.getString("currency"),
                    )
                },
                merchant = if (o.isNull("merchant")) null else o.optString("merchant").takeIf(String::isNotBlank),
                categoryId = if (o.isNull("category")) null else o.optString("category").takeIf(String::isNotBlank),
                doubts = (0 until doubts.length()).map { enumOr(doubts.getString(it), ModelDoubt.OTHER) }.toSet(),
            ),
            pass = enumOr(o.optString("pass"), InterpretationPass.PRIMARY),
            modelId = o.optString("modelId"),
            durationMillis = o.optLong("durationMs"),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
