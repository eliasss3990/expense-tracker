package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.catalog.CatalogUsage
import com.eliasgonzalez.expensetracker.domain.repository.CatalogRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Solo borra de verdad una entrada SIN datos asociados. Con datos que la
 * referencian responde IN_USE y la UI ofrece desactivarla en su lugar:
 * los gastos viejos tienen que seguir mostrandose igual que siempre.
 */
class DeleteCatalogEntry(
    private val catalog: CatalogRepository,
    private val usage: CatalogUsage,
    private val mutex: Mutex = Mutex(),
    private val awaitReady: suspend () -> Unit = {},
) {
    suspend operator fun invoke(kind: String, id: String): CatalogOutcome = mutex.withLock {
        // Sin hidratar, "en uso" daria false para categorias con gastos reales.
        awaitReady()
        val entry = catalog.find(kind, id)
            ?: return@withLock CatalogOutcome.Rejected(CatalogRejection.NOT_FOUND)
        if (entry.protected) return@withLock CatalogOutcome.Rejected(CatalogRejection.PROTECTED)
        if (usage.isInUse(kind, id)) return@withLock CatalogOutcome.Rejected(CatalogRejection.IN_USE)

        catalog.delete(kind, id)
        CatalogOutcome.Success()
    }
}
