package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.repository.CatalogRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Mutex COMPARTIDO por todos los casos de uso que escriben el catalogo
 * (mismo criterio que en CreateCandidate): sin esto, dos altas casi
 * simultaneas con el mismo nombre pasan las dos el chequeo de duplicado.
 */
class CreateCatalogEntry(
    private val catalog: CatalogRepository,
    private val mutex: Mutex = Mutex(),
    private val awaitReady: suspend () -> Boolean = { true },
    private val validKinds: Set<String> = CatalogKind.all,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(kind: String, label: String, color: String): CatalogOutcome =
        mutex.withLock {
            // Sin esperar la hidratacion, la lista en memoria esta vacia y
            // el chequeo de duplicados pasaria de largo.
            if (!awaitReady()) return@withLock CatalogOutcome.Rejected(CatalogRejection.NOT_READY)
            if (kind !in validKinds) return@withLock CatalogOutcome.Rejected(CatalogRejection.INVALID_KIND)
            if (!label.isValidCatalogLabel()) return@withLock CatalogOutcome.Rejected(CatalogRejection.INVALID_LABEL)
            if (!color.isValidCatalogColor()) return@withLock CatalogOutcome.Rejected(CatalogRejection.INVALID_COLOR)

            val clean = label.normalizedCatalogLabel()
            val sameKind = catalog.entries.value.filter { it.kind == kind }
            if (sameKind.any { it.label.equals(clean, ignoreCase = true) }) {
                return@withLock CatalogOutcome.Rejected(CatalogRejection.DUPLICATE_LABEL)
            }

            val entry = CatalogEntry(
                kind = kind,
                id = newId(),
                label = clean,
                color = color.uppercase(),
                sortOrder = (sameKind.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            )
            catalog.save(entry)
            CatalogOutcome.Success(entry)
        }
}
