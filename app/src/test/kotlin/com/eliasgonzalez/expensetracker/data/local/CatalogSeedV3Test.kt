package com.eliasgonzalez.expensetracker.data.local

import com.eliasgonzalez.expensetracker.domain.catalog.CatalogDefaults
import com.eliasgonzalez.expensetracker.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La migracion v2->v3 siembra CatalogSeedV3, una copia CONGELADA, no
 * CatalogDefaults (que cambia con el codigo). Hoy las dos son identicas; si
 * CatalogDefaults cambia (categoria nueva, renombre, se elimina el enum), el
 * primer test falla y obliga a decidir a conciencia si hace falta una
 * migracion nueva con su propia lista - en vez de que el resultado del upgrade
 * dependa en silencio de la version de la app.
 */
class CatalogSeedV3Test {

    @Test
    fun `hoy el seed congelado de v3 coincide con las categorias de fabrica vigentes`() {
        assertEquals(CatalogDefaults.categories, CatalogSeedV3.categories)
    }

    @Test
    fun `el seed v3 tiene un id por categoria historica, todos distintos`() {
        val ids = CatalogSeedV3.categories.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
        assertEquals(Category.entries.map { it.id }, ids)
    }

    @Test
    fun `solo Otro esta protegida en el seed v3`() {
        assertEquals(listOf("OTHER"), CatalogSeedV3.categories.filter { it.protected }.map { it.id })
        assertTrue(CatalogSeedV3.categories.all { it.builtIn && it.active })
    }
}
