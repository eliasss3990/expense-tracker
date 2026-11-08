package com.eliasgonzalez.expensetracker.ui.chat

import com.eliasgonzalez.expensetracker.domain.chat.ChatMetric
import com.eliasgonzalez.expensetracker.domain.chat.ChatProposal
import com.eliasgonzalez.expensetracker.domain.chat.ChatReply
import com.eliasgonzalez.expensetracker.domain.chat.ClarifyReason
import com.eliasgonzalez.expensetracker.domain.chat.ConfirmResult
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPresentationTest {
    private val zone = ZoneId.of("America/Asuncion")
    private val p = ChatPresentation({ id -> mapOf("FOOD" to "Comida", "TRANSPORT" to "Transporte")[id] ?: id }, zone)
    private val noon = LocalDate.of(2026, 11, 7).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun exp(amount: Long = 80_000, merchant: String = "Shell", cat: String = "TRANSPORT") =
        Expense(1, amount, "PYG", merchant, cat, "", noon, noon, ExpenseSource.MANUAL)

    @Test
    fun `los importes salen con punto de miles como en el resto de la app`() {
        assertEquals("₲80.000", p.money(80_000))
        assertEquals("₲1.250.000", p.money(1_250_000))
    }

    @Test
    fun `resumen de suma con rango y categoria`() {
        val r = ChatReply.Summary(ChatMetric.SUM, 92_000, 2, LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8), "FOOD")
        assertEquals("Gastaste ₲92.000 en Comida del 07/11 al 08/11 (2 gastos).", p.text(r))
    }

    @Test
    fun `un solo dia y sin gastos`() {
        val one = LocalDate.of(2026, 11, 8)
        assertEquals("No encontré gastos el 08/11.", p.text(ChatReply.Summary(ChatMetric.SUM, 0, 0, one, one, null)))
        assertEquals("Tuviste 1 gasto el 08/11.", p.text(ChatReply.Summary(ChatMetric.COUNT, 5, 1, one, one, null)))
    }

    @Test
    fun `la lista dice cuantos faltan por mostrar`() {
        val r = ChatReply.Listing(listOf(exp()), totalCount = 25, total = 500_000)
        val t = p.text(r)
        assertTrue(t.startsWith("25 gastos, ₲500.000 en total:"))
        assertTrue("Shell — ₲80.000" in t)
        assertTrue(t.endsWith("…y 24 más."))
    }

    @Test
    fun `la propuesta de cargar pide confirmacion con todos los datos y ofrece corregir`() {
        val t = p.text(ChatReply.Confirm(ChatProposal.Create(exp(12_000, "Cafe", "FOOD"))))
        assertTrue(t.startsWith("¿Son correctos los datos?"))
        for (piece in listOf("Comercio: Cafe", "Monto: ₲12.000", "Categoría: Comida", "Fecha: 07/11 12:00", "decime qué cambiar")) assertTrue(piece, piece in t)
    }

    @Test
    fun `la propuesta de editar marca solo lo que cambia`() {
        val before = exp(); val after = before.copy(amount = 50_000)
        val t = p.text(ChatReply.Confirm(ChatProposal.Edit(before, after)))
        assertTrue("Monto: ₲80.000 → ₲50.000" in t)
        assertTrue("→" !in t.substringAfter("Comercio:").substringBefore("\n"))
        assertTrue("Categoría: Transporte\n" in t)
    }

    @Test
    fun `borrar nombra el gasto y avisa que no se deshace`() {
        val t = p.text(ChatReply.Confirm(ChatProposal.Delete(exp())))
        assertTrue(t.startsWith("¿Borro este gasto?")); assertTrue("Shell" in t); assertTrue("No se puede deshacer" in t)
    }

    @Test
    fun `cada motivo de aclaracion tiene un texto propio`() {
        val texts = ClarifyReason.entries.map { p.text(ChatReply.Clarify(it, "x")) }
        assertEquals(ClarifyReason.entries.size, texts.toSet().size)
        assertTrue(texts.all { it.isNotBlank() })
    }

    @Test
    fun `la ayuda dice con claridad que no crea categorias ni cambia fechas`() {
        val t = p.text(ChatReply.Help)
        assertTrue("Categorías: crear una, renombrarla o borrarla" in t); assertTrue("color" in t); assertTrue("fecha" in t)
        assertTrue("lo confirmás vos" in t)
    }

    @Test
    fun `las propuestas de categorias dicen que cambia y aclaran lo de los gastos`() {
        val food = com.eliasgonzalez.expensetracker.domain.model.CatalogEntry("category", "FOOD", "Comida", "#F59E0B")
        assertTrue("Nombre: Mascotas" in p.text(ChatReply.Confirm(ChatProposal.CategoryCreate("Mascotas", "#22C55E"))))
        val rename = p.text(ChatReply.Confirm(ChatProposal.CategoryRename(food, "Alimentación")))
        assertTrue("Comida → Alimentación" in rename); assertTrue("se mantienen" in rename)
        assertTrue("No se puede deshacer" in p.text(ChatReply.Confirm(ChatProposal.CategoryDelete(food))))
        val off = p.text(ChatReply.Confirm(ChatProposal.CategoryDeactivate(food)))
        assertTrue("no se puede borrar" in off); assertTrue("conservan" in off)
    }

    @Test
    fun `la charla cortada a mitad de frase se recorta a la ultima oracion completa`() {
        assertEquals("Hola, decime qué querés consultar.", p.tidy("Hola, decime qué querés consultar."))
        assertEquals("Solo puedo ayudarte con tus gastos.", p.tidy("Solo puedo ayudarte con tus gastos. Mis categorías son FOOD, FUEL, T,"))
        assertEquals("Mis categorías definidas son FOOD, FUEL…", p.tidy("Mis categorías definidas son FOOD, FUEL, GRO"))
        assertEquals("", p.tidy("   "))
    }

    @Test
    fun `el resultado de confirmar se dice claro, incluido cuando no se toco nada`() {
        val d = ChatProposal.Delete(exp())
        assertEquals("Listo, borré Shell (₲80.000).", p.confirmed(ConfirmResult.DONE, d))
        assertTrue("no toqué nada" in p.confirmed(ConfirmResult.STALE, d))
        assertTrue("No pude guardarlo" in p.confirmed(ConfirmResult.FAILED, d))
    }
}
