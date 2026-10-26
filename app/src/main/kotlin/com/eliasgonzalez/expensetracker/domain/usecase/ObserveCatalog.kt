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
        catalog.entries.map { all ->
            all.filter { it.kind == kind && (!onlyActive || it.active) }
                .sortedBy { it.sortOrder }
        }
}
