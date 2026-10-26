package com.eliasgonzalez.expensetracker.domain.catalog

import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Antirregresion del seed: hasta que la UI migre al catalogo, el enum
 * `Category` es la fuente de verdad. Si alguien agrega una categoria al
 * enum y se olvida del seed (o al reves), estos tests fallan en vez de
 * dejar una categoria "fantasma" que existe en un lado y no en el otro.
 */
class CatalogDefaultsTest {

    private val seeded = CatalogDefaults.categories

    @Test
    fun `hay una entrada sembrada por cada categoria del enum, con el mismo id y nombre`() {
        assertEquals(Category.entries.map { it.id }, seeded.map { it.id })
        assertEquals(Category.entries.map { it.label }, seeded.map { it.label })
    }

    @Test
    fun `todas son de tipo categoria, activas y de fabrica`() {
        assertTrue(seeded.all { it.kind == CatalogKind.CATEGORY && it.active && it.builtIn })
    }

    @Test
    fun `solo Otro esta protegida`() {
        assertEquals(listOf(Category.OTHER.id), seeded.filter { it.protected }.map { it.id })
    }

    @Test
    fun `los colores son hex validos y el orden es unico`() {
        assertTrue(seeded.all { it.color.isValidCatalogColorForTest() })
        assertEquals(seeded.size, seeded.map { it.sortOrder }.toSet().size)
    }

    @Test
    fun `los ids sembrados no se repiten`() {
        assertEquals(seeded.size, seeded.map { it.id }.toSet().size)
    }

    private fun String.isValidCatalogColorForTest() = Regex("^#[0-9A-Fa-f]{6}$").matches(this)
}
