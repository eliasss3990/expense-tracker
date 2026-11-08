package com.eliasgonzalez.expensetracker.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryMemoryTest {
    private val other = "OTHER"

    @Test
    fun `repite la categoria que el usuario uso para ese comercio`() {
        val m = CategoryMemory(listOf("Shell" to "FUEL", "Shell" to "FUEL", "Netflix" to "SUBSCRIPTIONS"), other)
        assertEquals("FUEL", m.suggest("Shell"))
        assertEquals("SUBSCRIPTIONS", m.suggest("netflix"))
    }

    @Test
    fun `ignora mayusculas, tildes y signos al comparar comercios`() {
        val m = CategoryMemory(listOf("Café Central S.A." to "FOOD"), other)
        assertEquals("FOOD", m.suggest("CAFE CENTRAL S A"))
    }

    @Test
    fun `un comercio sin historial no tiene sugerencia`() {
        assertNull(CategoryMemory(listOf("Shell" to "FUEL"), other).suggest("Petrobras"))
        assertNull(CategoryMemory(emptyList(), other).suggest("Shell"))
        assertNull(CategoryMemory(listOf("Shell" to "FUEL"), other).suggest(null))
    }

    @Test
    fun `gana la mas usada y un empate no se adivina`() {
        val winner = CategoryMemory(listOf("X" to "FOOD", "X" to "FOOD", "X" to "SHOPPING"), other)
        assertEquals("FOOD", winner.suggest("X"))
        val tie = CategoryMemory(listOf("X" to "FOOD", "X" to "SHOPPING"), other)
        assertNull(tie.suggest("X"))
    }

    @Test
    fun `lo que quedo en el comodin no cuenta como una decision sobre el comercio`() {
        val m = CategoryMemory(listOf("X" to other, "X" to other, "X" to "FOOD"), other)
        assertEquals("FOOD", m.suggest("X"))
        assertNull(CategoryMemory(listOf("Y" to other), other).suggest("Y"))
    }

    @Test
    fun `un comercio vacio en el historial no se mezcla con nada`() {
        assertNull(CategoryMemory(listOf("" to "FOOD", "  " to "FOOD"), other).suggest(""))
    }
}
