package com.eliasgonzalez.expensetracker.domain.catalog

import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CategoryIds
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryDisplayTest {

    private val entries = CatalogDefaults.categories

    @Test
    fun `encuentra la entrada por id`() {
        assertEquals("Comida", entries.categoryFor(CategoryIds.FOOD).label)
    }

    @Test
    fun `una desactivada se sigue mostrando con su nombre y color (los gastos viejos no cambian)`() {
        val withInactive = entries.map { if (it.id == CategoryIds.FUEL) it.copy(active = false) else it }

        val shown = withInactive.categoryFor(CategoryIds.FUEL)

        assertEquals("Combustible", shown.label)
        assertEquals("#EF4444", shown.color)
    }

    @Test
    fun `un id desconocido se muestra como Otro, no rompe`() {
        assertEquals(CategoryIds.OTHER, entries.categoryFor("no-existe").id)
    }

    @Test
    fun `con el catalogo todavia vacio igual devuelve Otro`() {
        val shown = emptyList<CatalogEntry>().categoryFor(CategoryIds.FOOD)

        assertEquals(CategoryIds.OTHER, shown.id)
        assertEquals("Otro", shown.label)
    }

    @Test
    fun `una categoria renombrada se muestra con el nombre nuevo`() {
        val renamed = entries.map { if (it.id == CategoryIds.FOOD) it.copy(label = "Restaurantes") else it }

        assertEquals("Restaurantes", renamed.categoryFor(CategoryIds.FOOD).label)
    }

    @Test
    fun `un id desconocido se muestra con la entrada protegida del catalogo, no con un id fijo`() {
        val catalogo = listOf(
            CatalogEntry("category", "COMIDA", "Comida", "#F59E0B"),
            CatalogEntry("category", "RESTO", "Resto", "#6B7280", protected = true),
        )

        assertEquals("RESTO", catalogo.categoryFor("no-existe").id)
    }
}
