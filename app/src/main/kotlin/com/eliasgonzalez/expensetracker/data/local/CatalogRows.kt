package com.eliasgonzalez.expensetracker.data.local

import android.content.ContentValues
import android.database.Cursor
import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry

// Un solo lugar para el mapeo CatalogEntry <-> fila de catalog_entries:
// lo usan LocalCatalogRepository y el seed de DbHelper. Agregar una
// columna implica tocar CREATE TABLE (DbHelper) y estas dos funciones.

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
