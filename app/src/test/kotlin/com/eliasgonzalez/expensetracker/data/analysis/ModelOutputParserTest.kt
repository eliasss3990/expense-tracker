package com.eliasgonzalez.expensetracker.data.analysis

import com.eliasgonzalez.expensetracker.domain.analysis.AmountRole
import com.eliasgonzalez.expensetracker.domain.analysis.ModelDoubt
import com.eliasgonzalez.expensetracker.domain.analysis.MoneyDirection
import com.eliasgonzalez.expensetracker.domain.analysis.OperationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModelOutputParserTest {

    private val valid = """{"direction":"OUTGOING","status":"COMPLETED","amounts":[{"value":85000,"role":"OPERATION","currency":"PYG"},{"value":1200000,"role":"BALANCE","currency":"PYG"}],"merchant":"Supermercado Real","doubts":[]}"""

    @Test
    fun `lee una respuesta correcta`() {
        val i = ModelOutputParser.parse(valid)
        assertEquals(MoneyDirection.OUTGOING, i.direction)
        assertEquals(OperationStatus.COMPLETED, i.status)
        assertEquals(85_000L, i.operationAmount?.value)
        assertEquals(AmountRole.BALANCE, i.amounts[1].role)
        assertEquals("Supermercado Real", i.merchant)
        assertTrue(i.doubts.isEmpty())
    }

    @Test
    fun `tolera cercas de codigo y charla alrededor`() {
        val i = ModelOutputParser.parse("Claro, aqui esta:\n```json\n$valid\n```\nEspero que sirva {gracias}")
        assertEquals(85_000L, i.operationAmount?.value)
    }

    @Test
    fun `no se confunde con llaves dentro de un texto`() {
        val raw = """{"direction":"OUTGOING","status":"COMPLETED","amounts":[{"value":5,"role":"OPERATION","currency":"PYG"}],"merchant":"Tienda {Centro} \"24h\"","doubts":[]}"""
        assertEquals("Tienda {Centro} \"24h\"", ModelOutputParser.parse(raw).merchant)
    }

    @Test
    fun `acepta el importe como texto con separadores`() {
        for (v in listOf("\"85.000\"", "\"85,000\"", "85000.0", "\"85000\"")) {
            val raw = """{"direction":"OUTGOING","status":"COMPLETED","amounts":[{"value":$v,"role":"OPERATION","currency":"PYG"}],"doubts":[]}"""
            assertEquals(v, 85_000L, ModelOutputParser.parse(raw).operationAmount?.value)
        }
    }

    @Test
    fun `un importe con decimales reales no se adivina, se descarta`() {
        val raw = """{"direction":"OUTGOING","status":"COMPLETED","amounts":[{"value":"85.50","role":"OPERATION","currency":"USD"}],"doubts":[]}"""
        assertNull(ModelOutputParser.parse(raw).operationAmount)
    }

    @Test
    fun `valores de enum desconocidos caen al caso prudente`() {
        val i = ModelOutputParser.parse("""{"direction":"SIDEWAYS","status":"??","amounts":[{"value":1,"role":"BONUS"}],"doubts":["RARA"]}""")
        assertEquals(MoneyDirection.UNKNOWN, i.direction)
        assertEquals(OperationStatus.UNKNOWN, i.status)
        assertEquals(AmountRole.OTHER, i.amounts.single().role)
        assertEquals(setOf(ModelDoubt.OTHER), i.doubts)
    }

    @Test
    fun `comercio null o vacio queda sin comercio`() {
        for (m in listOf("null", "\"null\"", "\"\"", "\"  \"")) {
            val i = ModelOutputParser.parse("""{"direction":"OUTGOING","status":"COMPLETED","amounts":[],"merchant":$m,"doubts":[]}""")
            assertNull(m, i.merchant)
        }
    }

    @Test
    fun `una moneda que no es ISO queda en null`() {
        val i = ModelOutputParser.parse("""{"direction":"OUTGOING","status":"COMPLETED","amounts":[{"value":5,"role":"OPERATION","currency":"guaranies"}],"doubts":[]}""")
        assertNull(i.amounts.single().currency)
    }

    @Test
    fun `una salida ilegible lanza para reintentar y no inventa una interpretacion`() {
        for (raw in listOf("", "no se", "{\"direction\":", "[1,2,3]")) {
            try {
                ModelOutputParser.parse(raw)
                fail("debia lanzar para '$raw'")
            } catch (expected: ModelOutputException) {
                // ok
            }
        }
    }

    @Test
    fun `lee la categoria y trata null o vacio como ninguna`() {
        fun cat(v: String) = ModelOutputParser.parse("""{"direction":"OUTGOING","status":"COMPLETED","amounts":[],"category":$v,"doubts":[]}""").categoryId
        assertEquals("FUEL", cat("\"FUEL\""))
        assertNull(cat("null"))
        assertNull(cat("\"\""))
        assertNull(cat("\"null\""))
    }
}
