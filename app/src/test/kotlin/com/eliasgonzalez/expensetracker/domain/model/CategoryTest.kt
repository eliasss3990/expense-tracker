package com.eliasgonzalez.expensetracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryTest {

    @Test
    fun `fromId devuelve la categoria correcta para cada id conocido`() {
        Category.entries.forEach { category ->
            assertEquals(category, Category.fromId(category.id))
        }
    }

    @Test
    fun `fromId con id desconocido devuelve OTHER`() {
        assertEquals(Category.OTHER, Category.fromId("INEXISTENTE"))
        assertEquals(Category.OTHER, Category.fromId(""))
    }

    @Test
    fun `fromId es case-sensitive - id en minusculas no hace match`() {
        assertEquals(Category.OTHER, Category.fromId("mechanic"))
        assertEquals(Category.OTHER, Category.fromId("food"))
    }

    @Test
    fun `MECHANIC tiene id y label correctos`() {
        assertEquals("MECHANIC", Category.MECHANIC.id)
        assertEquals("Mecánico", Category.MECHANIC.label)
    }

    @Test
    fun `fromId MECHANIC devuelve MECHANIC`() {
        assertEquals(Category.MECHANIC, Category.fromId("MECHANIC"))
    }

    @Test
    fun `todas las categorias tienen ids unicos`() {
        val ids = Category.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `todas las categorias tienen labels no vacios`() {
        Category.entries.forEach { category ->
            assert(category.label.isNotBlank()) { "${category.name} tiene label vacío" }
        }
    }
}
