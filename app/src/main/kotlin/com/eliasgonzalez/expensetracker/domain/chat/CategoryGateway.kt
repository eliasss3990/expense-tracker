package com.eliasgonzalez.expensetracker.domain.chat

import com.eliasgonzalez.expensetracker.domain.catalog.CatalogUsage
import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.repository.CatalogRepository
import com.eliasgonzalez.expensetracker.domain.usecase.CatalogOutcome
import com.eliasgonzalez.expensetracker.domain.usecase.CreateCatalogEntry
import com.eliasgonzalez.expensetracker.domain.usecase.DeleteCatalogEntry
import com.eliasgonzalez.expensetracker.domain.usecase.RenameCatalogEntry
import com.eliasgonzalez.expensetracker.domain.usecase.SetCatalogEntryActive

/** Lo que el chat necesita del catalogo de categorias; las reglas (duplicados, protegidas, en uso) siguen siendo las de los casos de uso. */
interface CategoryGateway {
    /** Todas las categorias, activas o no. */
    fun categories(): List<CatalogEntry>
    fun find(id: String): CatalogEntry?
    fun isInUse(id: String): Boolean
    fun nextColor(): String
    suspend fun create(label: String, color: String): CatalogOutcome
    suspend fun rename(id: String, label: String): CatalogOutcome
    suspend fun delete(id: String): CatalogOutcome
    suspend fun setActive(id: String, active: Boolean): CatalogOutcome
}

class CatalogCategoryGateway(
    private val catalog: CatalogRepository,
    private val usage: CatalogUsage,
    private val createEntry: CreateCatalogEntry,
    private val renameEntry: RenameCatalogEntry,
    private val deleteEntry: DeleteCatalogEntry,
    private val setActiveEntry: SetCatalogEntryActive,
    /** Colores para categorias nuevas (hex #RRGGBB); se usa el primero que ninguna categoria tenga. */
    private val palette: List<String>,
) : CategoryGateway {
    private val kind = CatalogKind.CATEGORY

    override fun categories(): List<CatalogEntry> = catalog.entries.value.filter { it.kind == kind }.sortedBy { it.sortOrder }
    override fun find(id: String): CatalogEntry? = catalog.find(kind, id)
    override fun isInUse(id: String): Boolean = usage.isInUse(kind, id)

    override fun nextColor(): String {
        val used = categories().map { it.color.uppercase() }.toSet()
        return palette.firstOrNull { it.uppercase() !in used } ?: palette[categories().size % palette.size]
    }

    override suspend fun create(label: String, color: String) = createEntry(kind, label, color)
    override suspend fun rename(id: String, label: String) = renameEntry(kind, id, label)
    override suspend fun delete(id: String) = deleteEntry(kind, id)
    override suspend fun setActive(id: String, active: Boolean) = setActiveEntry(kind, id, active)
}
