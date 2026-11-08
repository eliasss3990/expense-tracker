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
