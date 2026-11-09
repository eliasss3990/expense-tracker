package com.eliasgonzalez.expensetracker.data.analysis

import com.eliasgonzalez.expensetracker.domain.analysis.CategoryOption
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSchemaTest {
    private val cats = listOf(CategoryOption("FOOD", "Comida"), CategoryOption("OTHER", "Otros"))
    private fun branches(c: List<CategoryOption> = cats): List<JSONObject> =
        JSONObject(ChatSchema.build(c)).getJSONArray("anyOf").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    private fun JSONObject.actionName() = getJSONObject("properties").getJSONObject("action").getJSONArray("enum").getString(0)

    @Test
    fun `hay una rama por accion y solo esas`() {
        assertEquals(
            listOf(
                "QUERY", "CREATE", "EDIT", "DELETE", "REVISE", "CATEGORY_CREATE", "CATEGORY_RENAME", "CATEGORY_DELETE",
                "REVISE_NAME", "CANCEL", "HELP", "SMALLTALK",
            ),
            branches().map { it.actionName() },
        )
    }

    @Test
    fun `cada rama exige todos sus campos y no admite otros`() {
        for (b in branches()) {
            assertFalse(b.getBoolean("additionalProperties"))
            assertEquals(b.getJSONObject("properties").keys().asSequence().toSet(), b.getJSONArray("required").strings().toSet())
            assertTrue("action" in b.getJSONArray("required").strings())
        }
    }

    @Test
    fun `cargar distingue el comercio de la descripcion y se puede editar la descripcion`() {
        val create = branches().first { it.actionName() == "CREATE" }.getJSONObject("properties")
        assertTrue(create.has("merchant") && create.has("description"))
        // la descripcion es una frase: mas larga que un nombre, pero acotada
        assertTrue(create.getJSONObject("description").getInt("maxLength") in 100..200)
        assertTrue(create.getJSONObject("merchant").getInt("maxLength") < create.getJSONObject("description").getInt("maxLength"))
        for (name in listOf("EDIT", "REVISE")) {
            val fields = branches().first { it.actionName() == name }.getJSONObject("properties").getJSONObject("field").getJSONArray("enum").strings()
            assertTrue("DESCRIPTION" in fields)
        }
    }

    @Test
    fun `la consulta tiene filtro por texto, orden y un limite acotado, y ya no hay una accion aparte para un gasto`() {
        val names = branches().map { it.actionName() }
        assertTrue("DESCRIBE" !in names)
        val q = branches().first { it.actionName() == "QUERY" }.getJSONObject("properties")
        assertEquals(listOf("RECENT", "AMOUNT"), q.getJSONObject("sort").getJSONArray("enum").strings())
        assertEquals(0, q.getJSONObject("limit").getInt("minimum")); assertEquals(20, q.getJSONObject("limit").getInt("maximum"))
        assertEquals("integer", q.getJSONObject("limit").getString("type"))
        assertTrue(q.getJSONObject("text").getInt("maxLength") in 1..200)
    }

    @Test
    fun `no hay ninguna accion que apunte a todos los gastos`() {
        val refs = branches().filter { it.getJSONObject("properties").has("reference") }
        assertEquals(listOf("EDIT", "DELETE"), refs.map { it.actionName() })
        for (r in refs) {
            val values = r.getJSONObject("properties").getJSONObject("reference").getJSONArray("enum").strings()
            assertEquals(listOf("LAST", "MOST_EXPENSIVE", "BY_FILTER"), values)
        }
    }

    @Test
    fun `la categoria solo puede ser una del catalogo del usuario`() {
        val create = branches().first { it.actionName() == "CREATE" }.getJSONObject("properties")
        assertEquals(listOf("FOOD", "OTHER"), create.getJSONObject("category").getJSONArray("enum").strings())
        val query = branches().first { it.actionName() == "QUERY" }.getJSONObject("properties")
        assertEquals(listOf("ALL", "FOOD", "OTHER"), query.getJSONObject("category").getJSONArray("enum").strings())
    }

    @Test
    fun `sin categorias el enum de crear no queda vacio`() {
        val create = branches(emptyList()).first { it.actionName() == "CREATE" }.getJSONObject("properties")
        assertTrue(create.getJSONObject("category").getJSONArray("enum").length() > 0)
    }

    @Test
    fun `los textos y la charla estan acotados`() {
        val talk = branches().first { it.actionName() == "SMALLTALK" }.getJSONObject("properties").getJSONObject("reply")
        assertTrue(talk.getInt("maxLength") in 1..300)
        val delete = branches().first { it.actionName() == "DELETE" }.getJSONObject("properties").getJSONObject("text")
        assertTrue(delete.getInt("maxLength") in 1..200)
    }
}
