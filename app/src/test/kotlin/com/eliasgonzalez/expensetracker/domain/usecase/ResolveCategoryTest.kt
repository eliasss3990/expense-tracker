package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.analysis.CategorySource
import com.eliasgonzalez.expensetracker.domain.catalog.CatalogDefaults
import com.eliasgonzalez.expensetracker.domain.model.CategoryIds
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveCategoryTest {
    private val expenses = FakeExpenseRepository()
    private val catalog = FakeCatalogRepository(CatalogDefaults.categories)
    private val resolve = ResolveCategory(expenses, catalog)

    private suspend fun past(merchant: String, category: String) {
        expenses.save(Expense(amount = 1, merchant = merchant, categoryId = category, occurredAt = 1, createdAt = 1, source = ExpenseSource.MANUAL))
    }

    @Test
    fun `la memoria del usuario gana sobre lo que diga el modelo`() = runTest {
        past("Shell", CategoryIds.FUEL)
        val c = resolve.resolve("Shell", modelCategoryId = CategoryIds.SHOPPING)
        assertEquals(CategoryIds.FUEL, c.categoryId)
        assertEquals(CategorySource.MEMORY, c.source)
    }

    @Test
    fun `un comercio nuevo usa la categoria del modelo si es activa`() = runTest {
        val c = resolve.resolve("Comercio Nuevo", modelCategoryId = CategoryIds.FOOD)
        assertEquals(CategoryIds.FOOD, c.categoryId)
        assertEquals(CategorySource.MODEL, c.source)
    }

    @Test
    fun `acepta que el modelo devuelva el nombre en vez del id`() = runTest {
        assertEquals(CategoryIds.FUEL, resolve.resolve("X", "combustible").categoryId)
    }

    @Test
    fun `una categoria inventada o ausente cae al comodin`() = runTest {
        for (asked in listOf("INVENTADA", "", null)) {
            val c = resolve.resolve("Nuevo", asked)
            assertEquals(CategoryIds.OTHER, c.categoryId)
            assertEquals(CategorySource.DEFAULT, c.source)
        }
    }

    @Test
    fun `una categoria desactivada no se sugiere ni por memoria ni por el modelo`() = runTest {
        past("Shell", CategoryIds.FUEL)
        val fuel = catalog.find("category", CategoryIds.FUEL)!!
        catalog.save(fuel.copy(active = false))
        assertEquals(CategorySource.DEFAULT, resolve.resolve("Shell", CategoryIds.FUEL).source)
        assertFalse(resolve.options().any { it.id == CategoryIds.FUEL })
    }

    @Test
    fun `al modelo se le ofrecen solo las categorias activas y nunca el comodin`() = runTest {
        val ids = resolve.options().map { it.id }
        assertFalse(CategoryIds.OTHER in ids)
        assertTrue(CategoryIds.FOOD in ids && CategoryIds.FUEL in ids)
    }

    @Test
    fun `si el modelo elige el comodin cuenta como por defecto y no como decision del modelo`() = runTest {
        assertEquals(CategorySource.DEFAULT, resolve.resolve("Nuevo", CategoryIds.OTHER).source)
    }

    @Test
    fun `lo que quedo en Otro no crea memoria`() = runTest {
        past("Raro", CategoryIds.OTHER)
        assertEquals(CategorySource.MODEL, resolve.resolve("Raro", CategoryIds.FOOD).source)
    }
}
