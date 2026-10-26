package com.eliasgonzalez.expensetracker.domain.catalog

import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.model.CategoryIds
import com.eliasgonzalez.expensetracker.domain.repository.CatalogRepository

/**
 * Regla de que categoria queda guardada en un gasto.
 *
 * DESACTIVAR una categoria solo la saca de los selectores para ELEGIR:
 * no cambia nada de lo que ya la usa (gastos, candidatos pendientes que
 * la sugieren, ediciones). Por eso aca una categoria desactivada se
 * respeta tal cual.
 *
 * El comodin "Otro" queda unicamente para un id que ya no existe en el
 * catalogo (ej. carrera entre borrar y un gasto nuevo), para no dejar un
 * gasto apuntando a la nada. Eliminar de verdad se bloquea mientras haya
 * gastos o candidatos pendientes que la usen (ver ExpenseCatalogUsage), asi
 * que en la practica esto es una red de seguridad, no el camino normal.
 */
interface CategoryPolicy {

    /** Categoria a guardar en un gasto NUEVO (alta manual, tile, candidato). */
    suspend fun forNewExpense(categoryId: String): String

    /**
     * Categoria a guardar al EDITAR un gasto existente. Si no se la cambia
     * se conserva tal cual (incluso si el id ya no existiera): editar el
     * monto nunca mueve un gasto de categoria.
     */
    suspend fun forEditedExpense(requestedId: String, currentId: String): String

    object None : CategoryPolicy {
        override suspend fun forNewExpense(categoryId: String) = categoryId
        override suspend fun forEditedExpense(requestedId: String, currentId: String) = requestedId
    }
}

class CatalogCategoryPolicy(
    private val catalog: CatalogRepository,
    private val awaitReady: suspend () -> Unit = {},
) : CategoryPolicy {

    override suspend fun forNewExpense(categoryId: String): String {
        // Antes de hidratar la lista esta vacia: sin esperar, todo caeria en "Otro".
        awaitReady()
        val categories = catalog.entries.value.filter { it.kind == CatalogKind.CATEGORY }
        // Sin ninguna categoria cargada (catalogo no disponible) no se puede
        // juzgar: se respeta lo pedido en vez de corromper el dato.
        if (categories.isEmpty()) return categoryId
        // Activa o no: lo unico que se rechaza es un id que no existe.
        if (categories.any { it.id == categoryId }) return categoryId
        // El comodin es la entrada protegida del propio catalogo; CategoryIds.OTHER
        // solo si (en un catalogo inconsistente) no hubiera ninguna.
        return categories.firstOrNull { it.protected }?.id ?: CategoryIds.OTHER
    }

    override suspend fun forEditedExpense(requestedId: String, currentId: String): String =
        if (requestedId == currentId) requestedId else forNewExpense(requestedId)
}
