package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * `onlyActive = true` (default) es para los selectores donde se ELIGE una
 * entrada; `false` es para listados y filtros que tienen que poder mostrar
 * tambien las desactivadas (ej. gastos viejos de una categoria apagada).
 */
class ObserveCatalog(private val catalog: CatalogRepository) {
    operator fun invoke(kind: String, onlyActive: Boolean = true): Flow<List<CatalogEntry>> =
        catalog.entries.map { all -> select(all, kind, onlyActive) }

    /**
     * Lectura puntual para quien no puede suscribirse a un Flow (vistas
     * nativas del overlay). Puede venir vacia si el catalogo aun no cargo:
     * quien la use decide el fallback.
     */
    fun snapshot(kind: String, onlyActive: Boolean = true): List<CatalogEntry> =
        select(catalog.entries.value, kind, onlyActive)

    private fun select(all: List<CatalogEntry>, kind: String, onlyActive: Boolean) =
        all.filter { it.kind == kind && (!onlyActive || it.active) }
            // La protegida ("Otro", el comodin) siempre al final: una categoria
            // nueva tiene sortOrder mayor, pero no debe quedar despues de ella.
            .sortedWith(compareBy<CatalogEntry>({ it.protected }, { it.sortOrder }))
}
