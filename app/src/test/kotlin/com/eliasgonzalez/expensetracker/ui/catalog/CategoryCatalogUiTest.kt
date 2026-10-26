package com.eliasgonzalez.expensetracker.ui.catalog

import com.eliasgonzalez.expensetracker.domain.catalog.CatalogDefaults
import com.eliasgonzalez.expensetracker.domain.usecase.CatalogRejection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryCatalogUiTest {

    @Test
    fun `todos los colores de la paleta son hex validos para CreateCatalogEntry`() {
        val hex = Regex("^#[0-9A-Fa-f]{6}$")
        assertTrue(CATEGORY_COLOR_CHOICES.all { hex.matches(it) })
    }

    @Test
    fun `la paleta no tiene colores repetidos`() {
        assertEquals(CATEGORY_COLOR_CHOICES.size, CATEGORY_COLOR_CHOICES.map { it.uppercase() }.toSet().size)
    }

    @Test
    fun `ningun color de la paleta repite el de una categoria de fabrica`() {
        val fabrica = CatalogDefaults.categories.map { it.color.uppercase() }.toSet()
        assertTrue(CATEGORY_COLOR_CHOICES.none { it.uppercase() in fabrica })
    }

    @Test
    fun `hay una paleta suficiente para elegir`() {
        assertTrue(CATEGORY_COLOR_CHOICES.size >= 8)
    }

    @Test
    fun `todo motivo de rechazo tiene un mensaje propio y no vacio`() {
        val mensajes = CatalogRejection.entries.map { categoryRejectionMessage(it) }

        assertTrue(mensajes.all { it.isNotBlank() })
        assertEquals(mensajes.size, mensajes.toSet().size)
    }

    @Test
    fun `el mensaje de IN_USE explica que solo se puede desactivar y menciona a los candidatos pendientes`() {
        val mensaje = categoryRejectionMessage(CatalogRejection.IN_USE)

        assertTrue(mensaje.contains("desactivar"))
        // El dominio tambien bloquea por candidatos pendientes: un "tiene gastos"
        // a secas contradeciria una fila que muestra "0 gastos".
        assertTrue(mensaje.contains("candidatos pendientes"))
    }

    @Test
    fun `la razon de en uso distingue gastos de candidatos pendientes`() {
        assertEquals("tiene 1 gasto", categoryInUseReason(1))
        assertEquals("tiene 7 gastos", categoryInUseReason(7))
        assertTrue(categoryInUseReason(0).contains("candidatos pendientes"))
    }

    @Test
    fun `el subtitulo de una fila cuenta los gastos en singular y plural`() {
        assertEquals("0 gastos", categoryRowSubtitle(active = true, protected = false, expenseCount = 0))
        assertEquals("1 gasto", categoryRowSubtitle(active = true, protected = false, expenseCount = 1))
        assertEquals("12 gastos", categoryRowSubtitle(active = true, protected = false, expenseCount = 12))
    }

    @Test
    fun `el subtitulo marca las inactivas y la que siempre esta activa`() {
        assertEquals("Inactiva · 3 gastos", categoryRowSubtitle(active = false, protected = false, expenseCount = 3))
        assertEquals("Siempre activa · 2 gastos", categoryRowSubtitle(active = true, protected = true, expenseCount = 2))
    }
}
