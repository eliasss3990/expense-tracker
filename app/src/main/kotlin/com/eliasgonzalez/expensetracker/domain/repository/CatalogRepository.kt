package com.eliasgonzalez.expensetracker.domain.repository

import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import kotlinx.coroutines.flow.StateFlow

interface CatalogRepository {
    /** Todas las entradas de todos los tipos, activas e inactivas. */
    val entries: StateFlow<List<CatalogEntry>>

    /** Inserta o reemplaza por (kind, id). */
    suspend fun save(entry: CatalogEntry)
    suspend fun delete(kind: String, id: String)
    fun find(kind: String, id: String): CatalogEntry?
}
