package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.repository.CatalogRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Renombrar no toca el id, asi que los gastos existentes no se ven afectados. */
class RenameCatalogEntry(
    private val catalog: CatalogRepository,
    private val mutex: Mutex = Mutex(),
    private val awaitReady: suspend () -> Unit = {},
) {
    suspend operator fun invoke(kind: String, id: String, newLabel: String): CatalogOutcome =
        mutex.withLock {
            awaitReady()
            val entry = catalog.find(kind, id)
                ?: return@withLock CatalogOutcome.Rejected(CatalogRejection.NOT_FOUND)
            if (!newLabel.isValidCatalogLabel()) {
                return@withLock CatalogOutcome.Rejected(CatalogRejection.INVALID_LABEL)
            }

            val clean = newLabel.normalizedCatalogLabel()
            val taken = catalog.entries.value.any {
                it.kind == kind && it.id != id && it.label.equals(clean, ignoreCase = true)
            }
            if (taken) return@withLock CatalogOutcome.Rejected(CatalogRejection.DUPLICATE_LABEL)

            val renamed = entry.copy(label = clean)
            catalog.save(renamed)
            CatalogOutcome.Success(renamed)
        }
}
