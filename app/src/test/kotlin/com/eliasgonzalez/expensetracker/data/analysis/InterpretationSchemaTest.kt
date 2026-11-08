package com.eliasgonzalez.expensetracker.data.analysis

import com.eliasgonzalez.expensetracker.domain.analysis.AmountRole
import com.eliasgonzalez.expensetracker.domain.analysis.CategoryOption
import com.eliasgonzalez.expensetracker.domain.analysis.MoneyDirection
import com.eliasgonzalez.expensetracker.domain.analysis.OperationStatus
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterpretationSchemaTest {

    private val cats = listOf(CategoryOption("FUEL", "Combustible"), CategoryOption("FOOD", "Comida"))
    private fun schema(c: List<CategoryOption> = cats) = JSONObject(InterpretationSchema.build(c))
    private fun props(s: JSONObject) = s.getJSONObject("properties")
    private fun values(a: JSONArray) = (0 until a.length()).map { a.get(it) as String }

    @Test
    fun `es JSON valido y exige todas las claves sin permitir otras`() {
        val s = schema()
        assertEquals(false, s.getBoolean("additionalProperties"))
        assertEquals(setOf("direction", "status", "amounts", "merchant", "category"), values(s.getJSONArray("required")).toSet())
        assertEquals(props(s).keys().asSequence().toSet(), values(s.getJSONArray("required")).toSet())
    }

    @Test
    fun `los enums salen de los enums del dominio y no se desincronizan`() {
        val p = props(schema())
        assertEquals(MoneyDirection.entries.map { it.name }, values(p.getJSONObject("direction").getJSONArray("enum")))
        assertEquals(OperationStatus.entries.map { it.name }, values(p.getJSONObject("status").getJSONArray("enum")))
        val item = p.getJSONObject("amounts").getJSONObject("items")
        assertEquals(AmountRole.entries.map { it.name }, values(props(item).getJSONObject("role").getJSONArray("enum")))
    }

    @Test
    fun `la categoria solo puede ser un id del catalogo del usuario o null`() {
        val any = props(schema()).getJSONObject("category").getJSONArray("anyOf")
        assertEquals(listOf("FUEL", "FOOD"), values(any.getJSONObject(0).getJSONArray("enum")))
        assertEquals("null", any.getJSONObject(1).getString("type"))
    }

    @Test
    fun `sin categorias la unica respuesta posible es null`() {
        assertEquals("null", props(schema(emptyList())).getJSONObject("category").getString("type"))
    }

    @Test
    fun `un cambio de catalogo cambia el esquema`() {
        val a = InterpretationSchema.build(cats)
        val b = InterpretationSchema.build(cats + CategoryOption("PETS", "Mascotas"))
        assertTrue("PETS" !in a && "PETS" in b)
    }

    @Test
    fun `acota el tamano de la salida`() {
        val p = props(schema())
        assertTrue(p.getJSONObject("amounts").getInt("maxItems") in 1..10)
        assertTrue(props(schema()).getJSONObject("merchant").getJSONArray("anyOf").getJSONObject(0).getInt("maxLength") <= 100)
    }

    @Test
    fun `los importes son enteros no negativos`() {
        val value = props(props(schema()).getJSONObject("amounts").getJSONObject("items")).getJSONObject("value")
        assertEquals("integer", value.getString("type"))
        assertEquals(0, value.getInt("minimum"))
    }
}
