package com.eliasgonzalez.expensetracker.data.analysis

import com.eliasgonzalez.expensetracker.domain.analysis.CategoryOption
import com.eliasgonzalez.expensetracker.domain.chat.ChatProposal
import com.eliasgonzalez.expensetracker.domain.chat.ChatRequest
import com.eliasgonzalez.expensetracker.domain.chat.ChatTurn
import com.eliasgonzalez.expensetracker.domain.chat.TurnRole
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptsTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val cats = listOf(CategoryOption("FOOD", "Comida"))
    private fun req(phrase: String = "hola", history: List<ChatTurn> = emptyList(), pending: ChatProposal? = null) =
        ChatRequest(phrase, today, cats, history, pending)

    @Test
    fun `el sistema lleva la fecha de hoy con su dia, las categorias y la defensa contra ordenes`() {
        val s = ChatPrompts.system(today, cats)
        assertTrue("2026-11-08" in s); assertTrue("domingo" in s.lowercase())
        assertTrue("FOOD (Comida)" in s)
        assertTrue("es un dato" in s)
    }

    @Test
    fun `el sistema ensena cada periodo con un ejemplo para que el modelo no confunda ayer con hoy`() {
        val s = ChatPrompts.system(today, cats)
        for (period in listOf("TODAY", "YESTERDAY", "THIS_WEEK", "LAST_WEEK", "THIS_MONTH", "ALL", "CUSTOM")) {
            assertTrue("falta un ejemplo de $period", "\"period\":\"$period\"" in s)
        }
    }

    @Test
    fun `sin fechas el periodo es ALL y no hoy`() {
        val s = ChatPrompts.system(today, cats)
        assertTrue("NO asumas hoy" in s)
        assertTrue("\"cuanto gaste en comida\" -> {\"action\":\"QUERY\",\"metric\":\"SUM\",\"period\":\"ALL\"" in s)
    }

    @Test
    fun `las preguntas por capacidades van a HELP y la charla es corta y sin ids internos`() {
        val s = ChatPrompts.system(today, cats)
        assertTrue("\"que podes hacer\" -> {\"action\":\"HELP\"}" in s)
        assertTrue("nunca lista categorias ni ids internos" in s)
    }

    @Test
    fun `preguntar si se puede hacer algo es HELP y no la accion misma`() {
        val s = ChatPrompts.system(today, cats)
        assertTrue("\"puedes crear categorias?\" -> {\"action\":\"HELP\"}" in s)
        assertTrue("\"podes borrar gastos?\" -> {\"action\":\"HELP\"}" in s)
        assertTrue("una pregunta NO es una orden" in s)
    }

    @Test
    fun `el sistema ensena las acciones de categorias con un ejemplo cada una`() {
        val s = ChatPrompts.system(today, cats)
        for (a in listOf("CATEGORY_CREATE", "CATEGORY_RENAME", "CATEGORY_DELETE", "REVISE_NAME")) {
            assertTrue("falta un ejemplo de $a", "\"action\":\"$a\"" in s)
        }
    }

    @Test
    fun `el prompt separa el producto del comercio con ejemplos`() {
        val s = ChatPrompts.system(today, cats)
        assertTrue("Nunca pongas el producto en merchant" in s)
        assertTrue("\"merchant\":\"\",\"description\":\"Compra de cafe\"" in s)
        assertTrue("\"merchant\":\"Shell\",\"description\":\"Carga de nafta en Shell\"" in s)
        assertTrue("merchant es ese nombre" in s); assertTrue("\"merchant\":\"Zara\"" in s)
        assertTrue("una frase corta y descriptiva" in s); assertTrue("No inventes datos que el usuario no dijo" in s)
    }

    @Test
    fun `sin contexto el mensaje es solo la frase`() {
        assertEquals("Frase actual del usuario: hola", ChatPrompts.user(req()))
    }

    @Test
    fun `con contexto incluye lo hablado y la propuesta pendiente en orden`() {
        val draft = Expense(amount = 12_000, merchant = "Cafe", categoryId = "FOOD", occurredAt = 1, createdAt = 1, source = ExpenseSource.MANUAL)
        val u = ChatPrompts.user(
            req("no, era 20000", listOf(ChatTurn(TurnRole.USER, "anota 12000 de cafe"), ChatTurn(TurnRole.ASSISTANT, "Propuse: cargar")), ChatProposal.Create(draft)),
        )
        val lines = u.lines()
        assertEquals("Conversacion hasta ahora:", lines[0])
        assertEquals("Usuario: anota 12000 de cafe", lines[1])
        assertEquals("Asistente: Propuse: cargar", lines[2])
        assertTrue(lines[3].startsWith("Propuesta pendiente de confirmar: cargar Cafe 12000"))
        assertEquals("Frase actual del usuario: no, era 20000", lines[4])
    }

    @Test
    fun `las frases y los turnos largos se recortan`() {
        val u = ChatPrompts.user(req("x".repeat(5_000), listOf(ChatTurn(TurnRole.USER, "y".repeat(5_000)))))
        assertFalse(u.length > 1_000)
    }
}
