package com.eliasgonzalez.expensetracker.domain.catalog

import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.model.CategoryIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las categorias de fabrica son una lista literal (CatalogDefaults) y sus ids
 * viven en CategoryIds. Si alguien agrega un id a una y se olvida de la otra,
 * estos tests fallan en vez de dejar una categoria que existe en un lado solo.
 */
class CatalogDefaultsTest {

    private val seeded = CatalogDefaults.categories

    @Test
    fun `hay una entrada sembrada por cada id de fabrica, en el mismo orden`() {
        assertEquals(CategoryIds.builtIn, seeded.map { it.id })
    }

    @Test
    fun `los nombres de fabrica son los que ve el usuario`() {
        assertEquals(
            listOf(
                "Comida", "Combustible", "Supermercado", "Entretenimiento", "Suscripciones",
                "Transporte", "Compras", "Salud", "Educación", "Mecánico", "Otro",
            ),
            seeded.map { it.label },
        )
    }

    @Test
    fun `todas son de tipo categoria, activas y de fabrica`() {
        assertTrue(seeded.all { it.kind == CatalogKind.CATEGORY && it.active && it.builtIn })
    }

    @Test
    fun `solo Otro esta protegida`() {
        assertEquals(listOf(CategoryIds.OTHER), seeded.filter { it.protected }.map { it.id })
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
