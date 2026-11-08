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
    fun `lo que se compro va en la descripcion y el lugar en el comercio`() {
        val c = parse("""{"action":"CREATE","amount":85000,"merchant":"Shell","description":"Nafta","category":"FOOD","date":""}""") as ChatIntent.Create
        assertEquals("Shell", c.merchant); assertEquals("Nafta", c.description)
        val noPlace = parse("""{"action":"CREATE","amount":12000,"merchant":"","description":"Cafe","category":"FOOD","date":""}""") as ChatIntent.Create
        assertEquals("", noPlace.merchant); assertEquals("Cafe", noPlace.description)
        // una salida vieja sin el campo sigue siendo valida
        assertEquals("", (parse("""{"action":"CREATE","amount":5,"merchant":"x","category":"FOOD","date":""}""") as ChatIntent.Create).description)
    }

    @Test
    fun `editar y corregir`() {
        assertEquals(
            ChatIntent.Edit(ExpenseRef.Last, EditableField.AMOUNT, "50000"),
            parse("""{"action":"EDIT","reference":"LAST","text":"","field":"AMOUNT","new_value":"50000"}"""),
        )
        assertEquals(ChatIntent.Revise(EditableField.AMOUNT, "20000"), parse("""{"action":"REVISE","field":"AMOUNT","new_value":"20000"}"""))
        assertEquals(ChatIntent.Cancel, parse("""{"action":"CANCEL"}"""))
        assertEquals(ChatIntent.Help, parse("""{"action":"HELP"}"""))
    }

    @Test
    fun `categorias`() {
        assertEquals(ChatIntent.CategoryCreate("Mascotas"), parse("""{"action":"CATEGORY_CREATE","name":" Mascotas "}"""))
        assertEquals(ChatIntent.CategoryRename("Comida", "Alimentacion"), parse("""{"action":"CATEGORY_RENAME","name":"Comida","new_name":"Alimentacion"}"""))
        assertEquals(ChatIntent.CategoryDelete("Suscripciones"), parse("""{"action":"CATEGORY_DELETE","name":"Suscripciones"}"""))
        assertEquals(ChatIntent.ReviseName("Pets"), parse("""{"action":"REVISE_NAME","new_name":"Pets"}"""))
    }

    @Test
    fun `una accion de categoria sin nombre es ilegible`() {
        for (bad in listOf("""{"action":"CATEGORY_CREATE","name":""}""", """{"action":"CATEGORY_CREATE"}""", """{"action":"CATEGORY_RENAME","name":"Comida","new_name":" "}""", """{"action":"CATEGORY_DELETE","name":""}""", """{"action":"REVISE_NAME","new_name":""}""")) {
            assertTrue("debia ser ilegible: $bad", parse(bad) is ChatIntent.Unreadable)
        }
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
    fun `una fecha presente pero ilegible es ilegible, no hoy ni sin limite`() {
        assertTrue(parse("""{"action":"CREATE","amount":5,"merchant":"x","category":"FOOD","date":"ayer"}""") is ChatIntent.Unreadable)
        assertTrue(parse("""{"action":"QUERY","metric":"SUM","period":"CUSTOM","from":"3 de noviembre","to":"2026-11-09","category":"ALL"}""") is ChatIntent.Unreadable)
        // vacio sigue significando "no hay fecha" (hoy al crear)
        assertEquals(null, (parse("""{"action":"CREATE","amount":5,"merchant":"x","category":"FOOD","date":""}""") as ChatIntent.Create).date)
    }

    @Test
    fun `un importe en texto solo se acepta con formato de importe`() {
        fun amount(v: String) = parse("""{"action":"CREATE","amount":"$v","merchant":"x","category":"FOOD","date":""}""")
        assertEquals(80_000L, (amount("80.000") as ChatIntent.Create).amount)
        assertEquals(1_250_000L, (amount("1.250.000") as ChatIntent.Create).amount)
        assertEquals(5_000L, (amount("5000") as ChatIntent.Create).amount)
        for (bad in listOf("1.5", "-5000", "2,5 mil", "abc", "12.34.56")) assertTrue("debia ser ilegible: $bad", amount(bad) is ChatIntent.Unreadable)
    }

}
