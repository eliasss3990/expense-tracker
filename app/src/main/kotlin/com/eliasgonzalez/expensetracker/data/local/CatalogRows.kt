package com.eliasgonzalez.expensetracker.data.local

import android.content.ContentValues
import android.database.Cursor
import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry

// Mapeo CatalogEntry <-> fila de catalog_entries para LocalCatalogRepository.
// OJO: el seed de la base NO pasa por aca: DbSchema (Kotlin puro, para poder
// probarlo en la JVM) tiene su propio INSERT con la lista de columnas. Agregar
// una columna a catalog_entries implica tocar TRES lugares: CREATE_CATALOG y el
// INSERT del seed (DbSchema) y estas dos funciones. DbMigrationTest falla si la
// entidad, la tabla y el seed no coinciden.

internal fun CatalogEntry.toContentValues() = ContentValues().apply {
    put("kind", kind)
    put("id", id)
    put("label", label)
    put("color", color)
    put("active", if (active) 1 else 0)
    put("sort_order", sortOrder)
    put("built_in", if (builtIn) 1 else 0)
    put("protected", if (protected) 1 else 0)
}

internal fun Cursor.toCatalogEntry() = CatalogEntry(
    kind = getString(getColumnIndexOrThrow("kind")),
    id = getString(getColumnIndexOrThrow("id")),
    label = getString(getColumnIndexOrThrow("label")),
    color = getString(getColumnIndexOrThrow("color")),
    active = getInt(getColumnIndexOrThrow("active")) == 1,
    sortOrder = getInt(getColumnIndexOrThrow("sort_order")),
    builtIn = getInt(getColumnIndexOrThrow("built_in")) == 1,
    protected = getInt(getColumnIndexOrThrow("protected")) == 1,
)
