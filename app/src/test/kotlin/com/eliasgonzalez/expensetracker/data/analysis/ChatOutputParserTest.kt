package com.eliasgonzalez.expensetracker.data.analysis

import com.eliasgonzalez.expensetracker.domain.chat.ChatIntent
import com.eliasgonzalez.expensetracker.domain.chat.ChatMetric
import com.eliasgonzalez.expensetracker.domain.chat.ChatPeriod
import com.eliasgonzalez.expensetracker.domain.chat.EditableField
import com.eliasgonzalez.expensetracker.domain.chat.ExpenseRef
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatOutputParserTest {
    private fun parse(s: String) = ChatOutputParser.parse(s)

    @Test
    fun `consulta con rango y categoria`() {
        val i = parse("""{"action":"QUERY","metric":"SUM","period":"CUSTOM","from":"2026-11-01","to":"2026-11-30","category":"FOOD"}""")
        assertEquals(ChatIntent.Query(ChatMetric.SUM, ChatPeriod.CUSTOM, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "FOOD"), i)
    }

    @Test
    fun `ALL significa sin filtro de categoria`() {
        val i = parse("""{"action":"QUERY","metric":"COUNT","period":"TODAY","from":"","to":"","category":"ALL"}""") as ChatIntent.Query
        assertEquals(null, i.categoryId)
    }

    @Test
    fun `el periodo lo elige el modelo y las fechas las calcula la app`() {
        val q = parse("""{"action":"QUERY","metric":"COUNT","period":"THIS_WEEK","from":"","to":"","category":"ALL"}""") as ChatIntent.Query
        assertEquals(ChatPeriod.THIS_WEEK, q.period); assertEquals(null, q.from); assertEquals(null, q.to)
        assertTrue(parse("""{"action":"QUERY","metric":"SUM","period":"CUALQUIERA","from":"","to":"","category":"ALL"}""") is ChatIntent.Unreadable)
        assertTrue(parse("""{"action":"QUERY","metric":"SUM","from":"","to":"","category":"ALL"}""") is ChatIntent.Unreadable)
    }

    @Test
    fun `referencias a un gasto`() {
        assertEquals(ChatIntent.Delete(ExpenseRef.Last), parse("""{"action":"DELETE","reference":"LAST","text":""}"""))
        assertEquals(ChatIntent.Describe(ExpenseRef.MostExpensive), parse("""{"action":"DESCRIBE","reference":"MOST_EXPENSIVE","text":""}"""))
        assertEquals(ChatIntent.Describe(ExpenseRef.ByText("farmacia")), parse("""{"action":"DESCRIBE","reference":"BY_FILTER","text":" farmacia "}"""))
    }

    @Test
    fun `por filtro sin texto no se adivina`() {
        assertTrue(parse("""{"action":"DELETE","reference":"BY_FILTER","text":""}""") is ChatIntent.Unreadable)
    }

    @Test
    fun `cargar acepta el importe como numero o como texto`() {
        assertEquals(12_000L, (parse("""{"action":"CREATE","amount":12000,"merchant":"Cafe","category":"FOOD","date":"2026-11-08"}""") as ChatIntent.Create).amount)
        assertEquals(80_000L, (parse("""{"action":"CREATE","amount":"80.000","merchant":"Nafta","category":"FOOD","date":"2026-11-08"}""") as ChatIntent.Create).amount)
        assertTrue(parse("""{"action":"CREATE","amount":12.5,"merchant":"x","category":"FOOD","date":"2026-11-08"}""") is ChatIntent.Unreadable)
        assertTrue(parse("""{"action":"CREATE","merchant":"x","category":"FOOD","date":"2026-11-08"}""") is ChatIntent.Unreadable)
    }

    @Test
    fun `editar y corregir`() {
        assertEquals(
            ChatIntent.Edit(ExpenseRef.Last, EditableField.AMOUNT, "50000"),
            parse("""{"action":"EDIT","reference":"LAST","text":"","field":"AMOUNT","new_value":"50000"}"""),
        )
        assertEquals(ChatIntent.Revise(EditableField.AMOUNT, "20000"), parse("""{"action":"REVISE","field":"AMOUNT","new_value":"20000"}"""))
        assertEquals(ChatIntent.Cancel, parse("""{"action":"CANCEL"}"""))
    }

    @Test
    fun `charla`() {
        assertEquals(ChatIntent.SmallTalk("Hola"), parse("""{"action":"SMALLTALK","reply":"Hola"}"""))
    }

    @Test
    fun `tolera texto y cercas de codigo alrededor del JSON`() {
        val raw = "Claro:\n```json\n{\"action\":\"CANCEL\"}\n```\nlisto"
        assertEquals(ChatIntent.Cancel, parse(raw))
    }

    @Test
    fun `lo que no se entiende es ilegible y nunca una accion`() {
        for (bad in listOf("", "hola", "{", """{"action":"BORRAR_TODO"}""", """{"action":"QUERY","metric":"MAGIA","period":"ALL"}""", """{"action":"EDIT","reference":"LAST","field":"COLOR","new_value":"x"}""", """{"action":"DELETE","reference":"ALL"}""")) {
            assertTrue("debia ser ilegible: $bad", parse(bad) is ChatIntent.Unreadable)
        }
    }

    @Test
    fun `una fecha mal escrita se ignora en vez de romper`() {
        val q = parse("""{"action":"QUERY","metric":"SUM","period":"CUSTOM","from":"ayer","to":"2026-11-08","category":"ALL"}""") as ChatIntent.Query
        assertEquals(null, q.from); assertEquals(LocalDate.of(2026, 11, 8), q.to)
    }
}
