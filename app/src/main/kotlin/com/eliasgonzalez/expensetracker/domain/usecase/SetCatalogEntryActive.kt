package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.repository.CatalogRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Desactivar saca la entrada de los selectores sin tocar los datos que ya
 * la usan. Reactivar siempre se permite. Las protegidas no se desactivan.
 */
class SetCatalogEntryActive(
    private val catalog: CatalogRepository,
    private val mutex: Mutex = Mutex(),
    private val awaitReady: suspend () -> Unit = {},
) {
    suspend operator fun invoke(kind: String, id: String, active: Boolean): CatalogOutcome =
        mutex.withLock {
            awaitReady()
            val entry = catalog.find(kind, id)
                ?: return@withLock CatalogOutcome.Rejected(CatalogRejection.NOT_FOUND)
            if (!active && entry.protected) {
                return@withLock CatalogOutcome.Rejected(CatalogRejection.PROTECTED)
            }
            if (entry.active == active) return@withLock CatalogOutcome.Success(entry)

            val updated = entry.copy(active = active)
            catalog.save(updated)
            CatalogOutcome.Success(updated)
        }
}
