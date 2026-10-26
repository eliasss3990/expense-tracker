package com.eliasgonzalez.expensetracker.domain.catalog

import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.model.Category
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
        assertEquals(Category.FOOD.id, CatalogCategoryPolicy(catalog()).forNewExpense(Category.FOOD.id))
    }

    @Test
    fun `una categoria DESACTIVADA se respeta - desactivar solo la oculta de los selectores`() = runTest {
        val policy = CatalogCategoryPolicy(catalog(Category.FUEL.id))

        assertEquals(Category.FUEL.id, policy.forNewExpense(Category.FUEL.id))
    }

    @Test
    fun `solo un id que no existe en el catalogo cae en Otro`() = runTest {
        assertEquals(
            Category.OTHER.id,
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

        assertEquals(Category.OTHER.id, CatalogCategoryPolicy(repo).forNewExpense("efectivo"))
    }

    @Test
    fun `sin ninguna categoria cargada no corrompe el dato - respeta lo pedido`() = runTest {
        assertEquals(
            Category.FUEL.id,
            CatalogCategoryPolicy(FakeCatalogRepository()).forNewExpense(Category.FUEL.id),
        )
    }

    @Test
    fun `al editar sin cambiar la categoria se conserva, aunque este desactivada o ya no exista`() = runTest {
        val policy = CatalogCategoryPolicy(catalog(Category.FUEL.id))

        assertEquals(Category.FUEL.id, policy.forEditedExpense(Category.FUEL.id, Category.FUEL.id))
        assertEquals("huerfana", policy.forEditedExpense("huerfana", "huerfana"))
    }

    @Test
    fun `al editar y cambiar a otra categoria existente se respeta, a una inexistente cae en Otro`() = runTest {
        val policy = CatalogCategoryPolicy(catalog())

        assertEquals(Category.HEALTH.id, policy.forEditedExpense(Category.HEALTH.id, Category.FOOD.id))
        assertEquals(Category.OTHER.id, policy.forEditedExpense("no-existe", Category.FOOD.id))
    }

    @Test
    fun `espera a la hidratacion antes de juzgar - no manda todo a Otro con la lista vacia`() = runTest {
        val repo = FakeCatalogRepository()
        val listo = CompletableDeferred<Unit>()
        val policy = CatalogCategoryPolicy(repo) { listo.await() }

        val resultado = async { policy.forNewExpense(Category.FOOD.id) }
        runCurrent()
        assertFalse(resultado.isCompleted)

        CatalogDefaults.categories.forEach { repo.save(it) }
        listo.complete(Unit)
        assertEquals(Category.FOOD.id, resultado.await())
    }
}
