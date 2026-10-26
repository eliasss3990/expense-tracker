package com.eliasgonzalez.expensetracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Estos ids estan GUARDADOS en la base de datos de los usuarios (gastos,
 * candidatos y el catalogo apuntan a ellos). Cambiar o reutilizar uno dejaria
 * datos reales apuntando a otra categoria, asi que los fijamos contra su texto
 * literal: si alguien "ordena" un nombre, este test falla.
 */
class CategoryIdsTest {

    @Test
    fun `los ids persistidos no cambian - son exactamente estos textos`() {
        assertEquals("FOOD", CategoryIds.FOOD)
        assertEquals("FUEL", CategoryIds.FUEL)
        assertEquals("GROCERIES", CategoryIds.GROCERIES)
        assertEquals("ENTERTAINMENT", CategoryIds.ENTERTAINMENT)
        assertEquals("SUBSCRIPTIONS", CategoryIds.SUBSCRIPTIONS)
        assertEquals("TRANSPORT", CategoryIds.TRANSPORT)
        assertEquals("SHOPPING", CategoryIds.SHOPPING)
        assertEquals("HEALTH", CategoryIds.HEALTH)
        assertEquals("EDUCATION", CategoryIds.EDUCATION)
        assertEquals("MECHANIC", CategoryIds.MECHANIC)
        assertEquals("OTHER", CategoryIds.OTHER)
    }

    @Test
    fun `builtIn lista cada id exactamente una vez, con Otro al final`() {
        assertEquals(11, CategoryIds.builtIn.size)
        assertEquals(CategoryIds.builtIn.size, CategoryIds.builtIn.toSet().size)
        assertEquals(CategoryIds.OTHER, CategoryIds.builtIn.last())
    }

    @Test
    fun `los ids son case-sensitive - uno en minusculas no es un id de fabrica`() {
        assertTrue("mechanic" !in CategoryIds.builtIn)
        assertTrue("MECHANIC" in CategoryIds.builtIn)
    }
}
