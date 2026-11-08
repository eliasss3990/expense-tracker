package com.eliasgonzalez.expensetracker.domain.chat

import com.eliasgonzalez.expensetracker.domain.catalog.CatalogUsage
import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.usecase.CreateCatalogEntry
import com.eliasgonzalez.expensetracker.domain.usecase.DeleteCatalogEntry
import com.eliasgonzalez.expensetracker.domain.usecase.FakeCatalogRepository
import com.eliasgonzalez.expensetracker.domain.usecase.RenameCatalogEntry
import com.eliasgonzalez.expensetracker.domain.usecase.SetCatalogEntryActive

/** Catalogo de categorias de prueba con los casos de uso reales detras del puerto del chat. */
internal class CategoryFixture(entries: List<CatalogEntry> = defaultEntries(), val inUse: MutableSet<String> = mutableSetOf()) {
    val catalog = FakeCatalogRepository(entries)
    private var counter = 0
    private val usage = CatalogUsage { _, id -> id in inUse }
    val gateway: CategoryGateway = CatalogCategoryGateway(
        catalog, usage,
        CreateCatalogEntry(catalog, newId = { "cat-${++counter}" }),
        RenameCatalogEntry(catalog),
        DeleteCatalogEntry(catalog, usage),
        SetCatalogEntryActive(catalog),
        palette = listOf("#EAB308", "#84CC16", "#22C55E"),
    )

    fun labels(): List<String> = gateway.categories().map { it.label }

    companion object {
        fun entry(id: String, label: String, color: String, order: Int, protected: Boolean = false, active: Boolean = true) =
            CatalogEntry(CatalogKind.CATEGORY, id, label, color, active = active, sortOrder = order, protected = protected)

        fun defaultEntries() = listOf(
            entry("FOOD", "Comida", "#F59E0B", 0),
            entry("TRANSPORT", "Transporte", "#3B82F6", 1),
            entry("FAST", "Comida rápida", "#EAB308", 2),
            entry("OTHER", "Otro", "#6B7280", 3, protected = true),
        )
    }
}
