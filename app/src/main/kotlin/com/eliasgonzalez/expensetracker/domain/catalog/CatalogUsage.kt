package com.eliasgonzalez.expensetracker.domain.catalog

import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository

/**
 * Puerto: responde si una entrada del catalogo esta referenciada por
 * datos existentes. Decide si se puede ELIMINAR (sin referencias) o solo
 * DESACTIVAR (con referencias). Cada tipo de catalogo aporta su criterio
 * sin que el caso de uso conozca los demas modelos.
 */
fun interface CatalogUsage {
    fun isInUse(kind: String, id: String): Boolean
}

/**
 * Una categoria esta en uso si algun gasto la tiene. Los candidatos
 * pendientes no cuentan: solo la sugieren, y al resolverse con una
 * categoria desactivada el comodin "Otro" cubre el caso.
 */
class ExpenseCatalogUsage(private val expenses: ExpenseRepository) : CatalogUsage {
    override fun isInUse(kind: String, id: String): Boolean = when (kind) {
        CatalogKind.CATEGORY -> expenses.expenses.value.any { it.categoryId == id }
        else -> false
    }
}
