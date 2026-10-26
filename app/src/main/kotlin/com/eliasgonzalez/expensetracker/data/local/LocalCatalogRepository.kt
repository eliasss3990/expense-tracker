package com.eliasgonzalez.expensetracker.data.local

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.repository.CatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalCatalogRepository(private val dbHelper: DbHelper) : CatalogRepository {

    private val _entries = MutableStateFlow<List<CatalogEntry>>(emptyList())
    override val entries: StateFlow<List<CatalogEntry>> = _entries.asStateFlow()

    // Mismo motivo que en LocalExpenseRepository: sin esto, dos escrituras
    // casi simultaneas leen la lista vieja y la ultima pisa a la primera.
    private val mutex = Mutex()

    suspend fun hydrate() = withContext(Dispatchers.IO) {
        val loaded = mutableListOf<CatalogEntry>()
        dbHelper.readableDatabase.query(
            "catalog_entries", null, null, null, null, null, "kind ASC, sort_order ASC",
        ).use { cursor -> while (cursor.moveToNext()) loaded.add(cursor.toCatalogEntry()) }
        _entries.value = loaded
    }

    override suspend fun save(entry: CatalogEntry) = withContext(Dispatchers.IO) {
        mutex.withLock {
            dbHelper.writableDatabase.insertWithOnConflict(
                "catalog_entries", null, entry.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE,
            )
            _entries.value = _entries.value
                .filterNot { it.kind == entry.kind && it.id == entry.id } + entry
        }
    }

    override suspend fun delete(kind: String, id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            dbHelper.writableDatabase.delete(
                "catalog_entries", "kind = ? AND id = ?", arrayOf(kind, id),
            )
            _entries.value = _entries.value.filterNot { it.kind == kind && it.id == id }
        }
        Unit
    }

    override fun find(kind: String, id: String): CatalogEntry? =
        _entries.value.find { it.kind == kind && it.id == id }

    private fun CatalogEntry.toContentValues() = ContentValues().apply {
        put("kind", kind)
        put("id", id)
        put("label", label)
        put("color", color)
        put("active", if (active) 1 else 0)
        put("sort_order", sortOrder)
        put("built_in", if (builtIn) 1 else 0)
        put("protected", if (protected) 1 else 0)
    }

    private fun Cursor.toCatalogEntry() = CatalogEntry(
        kind = getString(getColumnIndexOrThrow("kind")),
        id = getString(getColumnIndexOrThrow("id")),
        label = getString(getColumnIndexOrThrow("label")),
        color = getString(getColumnIndexOrThrow("color")),
        active = getInt(getColumnIndexOrThrow("active")) == 1,
        sortOrder = getInt(getColumnIndexOrThrow("sort_order")),
        builtIn = getInt(getColumnIndexOrThrow("built_in")) == 1,
        protected = getInt(getColumnIndexOrThrow("protected")) == 1,
    )
}
