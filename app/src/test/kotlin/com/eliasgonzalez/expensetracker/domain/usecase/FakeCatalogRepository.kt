package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Doble de prueba en memoria del catalogo - sin Android, sin SQLite. */
class FakeCatalogRepository(initial: List<CatalogEntry> = emptyList()) : CatalogRepository {
    private val _entries = MutableStateFlow(initial)
    override val entries: StateFlow<List<CatalogEntry>> = _entries.asStateFlow()
    private val mutex = Mutex()

    override suspend fun save(entry: CatalogEntry) = mutex.withLock {
        _entries.value = _entries.value.filterNot { it.kind == entry.kind && it.id == entry.id } + entry
    }

    override suspend fun delete(kind: String, id: String) = mutex.withLock {
        _entries.value = _entries.value.filterNot { it.kind == kind && it.id == id }
    }

    override fun find(kind: String, id: String): CatalogEntry? =
        _entries.value.find { it.kind == kind && it.id == id }
}
