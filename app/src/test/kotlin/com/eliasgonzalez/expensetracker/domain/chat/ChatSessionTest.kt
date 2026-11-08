package com.eliasgonzalez.expensetracker.domain.chat

import com.eliasgonzalez.expensetracker.domain.analysis.CategoryOption
import com.eliasgonzalez.expensetracker.domain.usecase.DeleteExpense
import com.eliasgonzalez.expensetracker.domain.usecase.EditExpense
import com.eliasgonzalez.expensetracker.domain.usecase.FakeActivityRepository
import com.eliasgonzalez.expensetracker.domain.usecase.FakeExpenseRepository
import com.eliasgonzalez.expensetracker.domain.usecase.RegisterExpense
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSessionTest {

    private val today = LocalDate.of(2026, 11, 8)
    private val categories = listOf(CategoryOption("FOOD", "Comida"), CategoryOption("OTHER", "Otros"))
    private val repo = FakeExpenseRepository()
    private val fixture = CategoryFixture()
    private val seen = mutableListOf<ChatRequest>()
    private var next: ChatIntent = ChatIntent.SmallTalk("hola")

    private fun newSession(): ChatSession {
        val assistant = ChatAssistant(
            interpreter = { seen += it; next }, expenses = repo,
            register = RegisterExpense(repo, FakeActivityRepository()), edit = EditExpense(repo, FakeActivityRepository()),
            delete = DeleteExpense(repo, FakeActivityRepository()), categories = fixture.gateway, zone = ZoneId.of("America/Asuncion"), now = { 1_000_000L },
        )
        return ChatSession(assistant)
    }

    private fun ChatSession.say(phrase: String, intent: ChatIntent): ChatReply = runBlocking {
        next = intent
        send(phrase, today, categories)
    }

    @Test
    fun `el modelo recibe lo hablado en este chat y la propuesta pendiente`() {
        val s = newSession()
        s.say("anota 12000 de cafe", ChatIntent.Create(12_000, "Cafe", "FOOD", null))
        s.say("no, era 20000", ChatIntent.Revise(EditableField.AMOUNT, "20000"))
        val second = seen.last()
        assertEquals(listOf(TurnRole.USER, TurnRole.ASSISTANT), second.history.map { it.role })
        assertEquals("anota 12000 de cafe", second.history.first().text)
        assertTrue(second.pending is ChatProposal.Create)
    }

    @Test
    fun `corregir la propuesta pendiente cambia el borrador y no guarda nada`() {
        val s = newSession()
        s.say("anota 12000 de cafe", ChatIntent.Create(12_000, "Cafe", "FOOD", null))
        val r = s.say("no, era 20000", ChatIntent.Revise(EditableField.AMOUNT, "20000")) as ChatReply.Confirm
        assertEquals(20_000L, (r.proposal as ChatProposal.Create).expense.amount)
        assertEquals(20_000L, (s.pending as ChatProposal.Create).expense.amount)
        assertTrue(repo.expenses.value.isEmpty())
    }

    @Test
    fun `confirmar registra lo corregido y limpia la propuesta`() = runBlocking {
        val s = newSession()
        s.say("anota 12000 de cafe", ChatIntent.Create(12_000, "Cafe", "FOOD", null))
        s.say("era 20000", ChatIntent.Revise(EditableField.AMOUNT, "20000"))
        assertEquals(ConfirmResult.DONE, s.confirm())
        assertEquals(listOf(20_000L), repo.expenses.value.map { it.amount })
        assertNull(s.pending)
        assertNull("sin propuesta no hay nada que confirmar", s.confirm())
    }

    @Test
    fun `corregir sin propuesta a la vista se avisa y no inventa un gasto`() {
        val s = newSession()
        val r = s.say("era 20000", ChatIntent.Revise(EditableField.AMOUNT, "20000"))
        assertEquals(ChatReply.Clarify(ClarifyReason.NOTHING_TO_REVISE), r)
        assertTrue(repo.expenses.value.isEmpty())
    }

    @Test
    fun `corregir con un borrado pendiente explica que hay una propuesta de borrar`() {
        runBlocking { repo.save(com.eliasgonzalez.expensetracker.domain.model.Expense(amount = 1, merchant = "x", occurredAt = 1, createdAt = 1, source = com.eliasgonzalez.expensetracker.domain.model.ExpenseSource.MANUAL)) }
        val s = newSession()
        s.say("borra el ultimo", ChatIntent.Delete(ExpenseRef.Last))
        assertEquals(ChatReply.Clarify(ClarifyReason.PENDING_IS_DELETE), s.say("mejor 5000", ChatIntent.Revise(EditableField.AMOUNT, "5000")))
        assertNotNull("la propuesta de borrar sigue a la vista", s.pending)
    }

    @Test
    fun `cancelar descarta la propuesta, y una consulta en medio no la descarta`() {
        val s = newSession()
        s.say("anota 5000", ChatIntent.Create(5_000, "x", null, null))
        s.say("cuanto gaste", ChatIntent.Query(ChatMetric.SUM, ChatPeriod.ALL, null, null, null))
        assertNotNull("una consulta no descarta la propuesta", s.pending)
        assertEquals(ChatReply.Cancelled, s.say("olvidalo", ChatIntent.Cancel))
        assertNull(s.pending)
    }

    @Test
    fun `un chat nuevo empieza limpio y sin nada del anterior`() {
        val first = newSession()
        first.say("anota 5000", ChatIntent.Create(5_000, "x", null, null))
        val second = newSession()
        second.say("hola", ChatIntent.SmallTalk("hola"))
        assertTrue(seen.last().history.isEmpty())
        assertNull(seen.last().pending)
        assertNull(second.pending)
    }

    @Test
    fun `el contexto esta acotado y olvida lo mas viejo`() {
        val s = newSession()
        repeat(12) { s.say("frase $it", ChatIntent.SmallTalk("ok $it")) }
        assertEquals(8, s.history.size)
        assertEquals("frase 8", s.history.first().text)
    }

    @Test
    fun `la propuesta pendiente de una edicion se corrige sin perder el antes`() = runBlocking {
        repo.save(com.eliasgonzalez.expensetracker.domain.model.Expense(
            amount = 12_000, merchant = "Cafe", categoryId = "FOOD", occurredAt = 1L, createdAt = 1L,
            source = com.eliasgonzalez.expensetracker.domain.model.ExpenseSource.MANUAL,
        ))
        val s = newSession()
        s.say("cambia el ultimo a 50000", ChatIntent.Edit(ExpenseRef.Last, EditableField.AMOUNT, "50000"))
        s.say("mejor 55000", ChatIntent.Revise(EditableField.AMOUNT, "55000"))
        val p = s.pending as ChatProposal.Edit
        assertEquals(12_000L, p.before.amount)
        assertEquals(55_000L, p.after.amount)
    }
}
