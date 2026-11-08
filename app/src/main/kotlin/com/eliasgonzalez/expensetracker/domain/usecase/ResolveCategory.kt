package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.analysis.CategoryChoice
import com.eliasgonzalez.expensetracker.domain.analysis.CategoryMemory
import com.eliasgonzalez.expensetracker.domain.analysis.CategoryOption
import com.eliasgonzalez.expensetracker.domain.analysis.CategorySource
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.model.CategoryIds
import com.eliasgonzalez.expensetracker.domain.repository.CatalogRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository

/**
 * Decide la categoria de un gasto detectado. Orden de prioridad:
 * 1. la que el propio usuario ya uso para ese comercio (memoria),
 * 2. la que elige el modelo entre las categorias ACTIVAS del catalogo,
 * 3. el comodin del catalogo.
 * Una categoria desactivada nunca se sugiere (solo se oculta de los selectores para elegir).
 */
class ResolveCategory(
    private val expenses: ExpenseRepository,
    private val catalog: CatalogRepository,
    private val awaitReady: suspend () -> Boolean = { true },
) {
    /** Lo que se le ofrece al modelo: las categorias activas, menos el comodin (que es el "ninguna"). */
    suspend fun options(): List<CategoryOption> {
        awaitReady()
        return activeCategories().filterNot { it.protected }.sortedBy { it.sortOrder }
            .map { CategoryOption(it.id, it.label) }
    }

    suspend fun resolve(merchant: String?, modelCategoryId: String?): CategoryChoice {
        awaitReady()
        val active = activeCategories()
        val activeIds = active.map { it.id }.toSet()
        val wildcard = catalog.entries.value
            .firstOrNull { it.kind == CatalogKind.CATEGORY && it.protected }?.id ?: CategoryIds.OTHER

        val memory = CategoryMemory(expenses.expenses.value.map { it.merchant to it.categoryId }, wildcard)
        memory.suggest(merchant)?.takeIf { it in activeIds }?.let { return CategoryChoice(it, CategorySource.MEMORY) }
        // El modelo debe devolver el id; si devuelve el nombre ("Combustible") tambien se acepta.
        modelCategoryId?.let { asked -> active.firstOrNull { it.id == asked || sameText(it.label, asked) } }
            ?.takeIf { it.id != wildcard }
            ?.let { return CategoryChoice(it.id, CategorySource.MODEL) }
        return CategoryChoice(wildcard, CategorySource.DEFAULT)
    }

    private fun sameText(a: String, b: String) = CategoryMemory.normalize(a) == CategoryMemory.normalize(b)

    private fun activeCategories() =
        catalog.entries.value.filter { it.kind == CatalogKind.CATEGORY && it.active }
}
