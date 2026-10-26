package com.eliasgonzalez.expensetracker.domain.catalog

import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.model.CategoryIds
import com.eliasgonzalez.expensetracker.domain.usecase.FakeCatalogRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CategoryPolicyTest {

    private fun catalog(vararg inactiveIds: String) = FakeCatalogRepository(
        CatalogDefaults.categories.map { if (it.id in inactiveIds) it.copy(active = false) else it }
    )

    @Test
    fun `una categoria activa se respeta en un gasto nuevo`() = runTest {
        assertEquals(CategoryIds.FOOD, CatalogCategoryPolicy(catalog()).forNewExpense(CategoryIds.FOOD))
    }

    @Test
    fun `una categoria DESACTIVADA se respeta - desactivar solo la oculta de los selectores`() = runTest {
        val policy = CatalogCategoryPolicy(catalog(CategoryIds.FUEL))

        assertEquals(CategoryIds.FUEL, policy.forNewExpense(CategoryIds.FUEL))
    }

    @Test
    fun `solo un id que no existe en el catalogo cae en Otro`() = runTest {
        assertEquals(
            CategoryIds.OTHER,
            CatalogCategoryPolicy(catalog()).forNewExpense("borrada-por-una-carrera"),
        )
    }

    @Test
    fun `una categoria propia del usuario se respeta, este activa o no`() = runTest {
        val repo = catalog()
        repo.save(CatalogEntry(CatalogKind.CATEGORY, "mascotas-1", "Mascotas", "#AA00FF", active = false, sortOrder = 99))

        assertEquals("mascotas-1", CatalogCategoryPolicy(repo).forNewExpense("mascotas-1"))
    }

    @Test
    fun `una entrada de otro tipo no cuenta como categoria`() = runTest {
        val repo = catalog()
        repo.save(CatalogEntry("payment_method", "efectivo", "Efectivo", "#112233"))

        assertEquals(CategoryIds.OTHER, CatalogCategoryPolicy(repo).forNewExpense("efectivo"))
    }

    @Test
    fun `sin ninguna categoria cargada no corrompe el dato - respeta lo pedido`() = runTest {
        assertEquals(
            CategoryIds.FUEL,
            CatalogCategoryPolicy(FakeCatalogRepository()).forNewExpense(CategoryIds.FUEL),
        )
    }

    @Test
    fun `al editar sin cambiar la categoria se conserva, aunque este desactivada o ya no exista`() = runTest {
        val policy = CatalogCategoryPolicy(catalog(CategoryIds.FUEL))

        assertEquals(CategoryIds.FUEL, policy.forEditedExpense(CategoryIds.FUEL, CategoryIds.FUEL))
        assertEquals("huerfana", policy.forEditedExpense("huerfana", "huerfana"))
    }

    @Test
    fun `al editar y cambiar a otra categoria existente se respeta, a una inexistente cae en Otro`() = runTest {
        val policy = CatalogCategoryPolicy(catalog())

        assertEquals(CategoryIds.HEALTH, policy.forEditedExpense(CategoryIds.HEALTH, CategoryIds.FOOD))
        assertEquals(CategoryIds.OTHER, policy.forEditedExpense("no-existe", CategoryIds.FOOD))
    }

    @Test
    fun `espera a la hidratacion antes de juzgar - no manda todo a Otro con la lista vacia`() = runTest {
        val repo = FakeCatalogRepository()
        val listo = CompletableDeferred<Unit>()
        val policy = CatalogCategoryPolicy(repo) { listo.await() }

        val resultado = async { policy.forNewExpense(CategoryIds.FOOD) }
        runCurrent()
        assertFalse(resultado.isCompleted)

        CatalogDefaults.categories.forEach { repo.save(it) }
        listo.complete(Unit)
        assertEquals(CategoryIds.FOOD, resultado.await())
    }

    @Test
    fun `el comodin es la entrada protegida del catalogo, no un id fijo`() = runTest {
        // Catalogo cuyo comodin NO se llama OTHER: la politica tiene que caer en el.
        val repo = FakeCatalogRepository(
            listOf(
                CatalogEntry(CatalogKind.CATEGORY, "COMIDA", "Comida", "#F59E0B", sortOrder = 0),
                CatalogEntry(CatalogKind.CATEGORY, "RESTO", "Resto", "#6B7280", sortOrder = 1, protected = true),
            )
        )

        assertEquals("RESTO", CatalogCategoryPolicy(repo).forNewExpense("id-inexistente"))
    }
}
