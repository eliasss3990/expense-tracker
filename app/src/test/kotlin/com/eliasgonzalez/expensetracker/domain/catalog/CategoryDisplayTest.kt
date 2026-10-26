package com.eliasgonzalez.expensetracker.domain.catalog

import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryDisplayTest {

    private val entries = CatalogDefaults.categories

    @Test
    fun `encuentra la entrada por id`() {
        assertEquals("Comida", entries.categoryFor(Category.FOOD.id).label)
    }

    @Test
    fun `una desactivada se sigue mostrando con su nombre y color (los gastos viejos no cambian)`() {
        val withInactive = entries.map { if (it.id == Category.FUEL.id) it.copy(active = false) else it }

        val shown = withInactive.categoryFor(Category.FUEL.id)

        assertEquals("Combustible", shown.label)
        assertEquals("#EF4444", shown.color)
    }

    @Test
    fun `un id desconocido se muestra como Otro, no rompe`() {
        assertEquals(Category.OTHER.id, entries.categoryFor("no-existe").id)
    }

    @Test
    fun `con el catalogo todavia vacio igual devuelve Otro`() {
        val shown = emptyList<CatalogEntry>().categoryFor(Category.FOOD.id)

        assertEquals(Category.OTHER.id, shown.id)
        assertEquals("Otro", shown.label)
    }

    @Test
    fun `una categoria renombrada se muestra con el nombre nuevo`() {
        val renamed = entries.map { if (it.id == Category.FOOD.id) it.copy(label = "Restaurantes") else it }

        assertEquals("Restaurantes", renamed.categoryFor(Category.FOOD.id).label)
    }
}
