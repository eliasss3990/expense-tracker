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

class ChatCategoryTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val repo = FakeExpenseRepository()
    private val fixture = CategoryFixture()
    private var next: ChatIntent = ChatIntent.Help
    private val session = ChatSession(
        ChatAssistant(
            interpreter = { next }, expenses = repo,
            register = RegisterExpense(repo, FakeActivityRepository()), edit = EditExpense(repo, FakeActivityRepository()),
            delete = DeleteExpense(repo, FakeActivityRepository()), categories = fixture.gateway,
            zone = ZoneId.of("America/Asuncion"), now = { 1_000_000L },
        ),
    )
    private val options = listOf(CategoryOption("FOOD", "Comida"))

    private fun say(intent: ChatIntent): ChatReply = runBlocking { next = intent; session.send("x", today, options) }
    private fun clarify(r: ChatReply) = (r as ChatReply.Clarify).reason
    private fun proposal(r: ChatReply) = (r as ChatReply.Confirm).proposal

    @Test
    fun `crear una categoria NO la crea al proponer, solo al confirmar, con un color que ninguna tenga`() = runBlocking {
        val before = fixture.labels()
        val p = proposal(say(ChatIntent.CategoryCreate("  Mascotas  "))) as ChatProposal.CategoryCreate
        assertEquals("Mascotas", p.label); assertEquals("#84CC16", p.color) // #EAB308 ya lo usa "Comida rápida"
        assertEquals(before, fixture.labels())
        assertEquals(ConfirmResult.DONE, session.confirm())
        assertEquals(before + "Mascotas", fixture.labels())
        assertEquals("#84CC16", fixture.gateway.categories().last().color)
    }

    @Test
    fun `un nombre repetido sin importar mayusculas ni acentos, o invalido, se pregunta`() {
        assertEquals(ClarifyReason.DUPLICATE_CATEGORY, clarify(say(ChatIntent.CategoryCreate("comida"))))
        assertEquals(ClarifyReason.DUPLICATE_CATEGORY, clarify(say(ChatIntent.CategoryCreate("COMIDA RAPIDA"))))
        assertEquals(ClarifyReason.INVALID_CATEGORY_NAME, clarify(say(ChatIntent.CategoryCreate("   "))))
        assertEquals(ClarifyReason.INVALID_CATEGORY_NAME, clarify(say(ChatIntent.CategoryCreate("x".repeat(41)))))
        assertEquals(4, fixture.labels().size)
    }

    @Test
    fun `renombrar propone el cambio y solo confirmar lo aplica, sin tocar el id`() = runBlocking {
        val p = proposal(say(ChatIntent.CategoryRename("transporte", "Movilidad"))) as ChatProposal.CategoryRename
        assertEquals("Transporte", p.before.label)
        assertEquals("Transporte", fixture.gateway.find("TRANSPORT")!!.label)
        assertEquals(ConfirmResult.DONE, session.confirm())
        assertEquals("Movilidad", fixture.gateway.find("TRANSPORT")!!.label)
    }

    @Test
    fun `renombrar rechaza el mismo nombre, uno ocupado, y categorias que no existen o son ambiguas`() {
        assertEquals(ClarifyReason.DUPLICATE_CATEGORY, clarify(say(ChatIntent.CategoryRename("Transporte", "Transporte"))))
        assertEquals(ClarifyReason.DUPLICATE_CATEGORY, clarify(say(ChatIntent.CategoryRename("Transporte", "comida"))))
        assertEquals(ClarifyReason.CATEGORY_NOT_FOUND, clarify(say(ChatIntent.CategoryRename("Viajes", "Otra"))))
        // "o" aparece en varias: sin coincidencia exacta es ambiguo y no se adivina
        assertEquals(ClarifyReason.AMBIGUOUS_CATEGORY, clarify(say(ChatIntent.CategoryRename("o", "Otra"))))
    }

    @Test
    fun `una coincidencia exacta gana sobre una que solo contiene el texto`() {
        // "Comida" esta contenida en "Comida rapida", pero es exactamente "Comida"
        val p = proposal(say(ChatIntent.CategoryRename("comida", "Alimentos"))) as ChatProposal.CategoryRename
        assertEquals("FOOD", p.before.id)
    }

    @Test
    fun `borrar una categoria sin gastos se propone y se borra al confirmar`() = runBlocking {
        val p = proposal(say(ChatIntent.CategoryDelete("Transporte"))) as ChatProposal.CategoryDelete
        assertEquals("TRANSPORT", p.entry.id)
        assertNotNull(fixture.gateway.find("TRANSPORT"))
        assertEquals(ConfirmResult.DONE, session.confirm())
        assertNull(fixture.gateway.find("TRANSPORT"))
    }

    @Test
    fun `una categoria con gastos no se borra, se propone desactivarla, como en la pantalla de categorias`() = runBlocking {
        fixture.inUse += "FOOD"
        val p = proposal(say(ChatIntent.CategoryDelete("Comida"))) as ChatProposal.CategoryDeactivate
        assertEquals("FOOD", p.entry.id)
        assertEquals(ConfirmResult.DONE, session.confirm())
        assertEquals(false, fixture.gateway.find("FOOD")!!.active)
        // ya desactivada y en uso: no hay nada mas que proponer
        assertEquals(ClarifyReason.ALREADY_INACTIVE, clarify(say(ChatIntent.CategoryDelete("Comida"))))
    }

    @Test
    fun `la categoria de respaldo no se borra ni se desactiva`() {
        assertEquals(ClarifyReason.PROTECTED_CATEGORY, clarify(say(ChatIntent.CategoryDelete("Otro"))))
        fixture.inUse += "OTHER"
        assertEquals(ClarifyReason.PROTECTED_CATEGORY, clarify(say(ChatIntent.CategoryDelete("Otro"))))
    }

    @Test
    fun `si la categoria cambio entre la propuesta y la confirmacion no se toca nada`() = runBlocking {
        say(ChatIntent.CategoryDelete("Transporte"))
        runBlocking { fixture.gateway.rename("TRANSPORT", "Movilidad") }
        assertEquals(ConfirmResult.STALE, session.confirm())
        assertEquals("Movilidad", fixture.gateway.find("TRANSPORT")!!.label)
    }

    @Test
    fun `renombrar tampoco se aplica si la categoria cambio desde la propuesta`() = runBlocking {
        say(ChatIntent.CategoryRename("Transporte", "Movilidad"))
        fixture.gateway.rename("TRANSPORT", "Viajes")
        assertEquals(ConfirmResult.STALE, session.confirm())
        assertEquals("Viajes", fixture.gateway.find("TRANSPORT")!!.label)
    }

    @Test
    fun `si alguien creo el mismo nombre o la categoria paso a tener gastos, es STALE y no un error`() = runBlocking {
        say(ChatIntent.CategoryCreate("Mascotas"))
        fixture.gateway.create("mascotas", "#22C55E")
        assertEquals(ConfirmResult.STALE, session.confirm())
        assertEquals(1, fixture.labels().count { it.equals("mascotas", ignoreCase = true) })
        // el borrado propuesto sin gastos, y justo despues la categoria pasa a usarse
        say(ChatIntent.CategoryDelete("Transporte"))
        fixture.inUse += "TRANSPORT"
        assertEquals(ConfirmResult.STALE, session.confirm())
        assertNotNull(fixture.gateway.find("TRANSPORT"))
    }

    @Test
    fun `corregir el nombre de la propuesta pendiente funciona para crear y renombrar`() = runBlocking {
        say(ChatIntent.CategoryCreate("Mascotas"))
        val fixed = proposal(say(ChatIntent.ReviseName("Pets"))) as ChatProposal.CategoryCreate
        assertEquals("Pets", fixed.label)
        assertEquals("Pets", (session.pending as ChatProposal.CategoryCreate).label)
        session.cancel()
        say(ChatIntent.CategoryRename("Transporte", "Movilidad"))
        val r = proposal(say(ChatIntent.ReviseName("Viajes"))) as ChatProposal.CategoryRename
        assertEquals("Viajes", r.newLabel); assertEquals("Transporte", r.before.label)
        assertEquals(4, fixture.labels().size)
    }

    @Test
    fun `corregir con otra clase de propuesta pendiente, o ninguna, se explica y no inventa`() {
        assertEquals(ClarifyReason.NOTHING_TO_REVISE, clarify(say(ChatIntent.ReviseName("x"))))
        say(ChatIntent.Create(5_000, "Cafe", null, null))
        assertEquals(ClarifyReason.WRONG_PENDING_KIND, clarify(say(ChatIntent.ReviseName("Pets"))))
        session.cancel()
        say(ChatIntent.CategoryCreate("Mascotas"))
        assertEquals(ClarifyReason.WRONG_PENDING_KIND, clarify(say(ChatIntent.Revise(EditableField.AMOUNT, "5000"))))
        assertEquals(ClarifyReason.DUPLICATE_CATEGORY, clarify(say(ChatIntent.ReviseName("Comida"))))
        assertTrue("la propuesta original sigue a la vista", session.pending is ChatProposal.CategoryCreate)
    }
}
