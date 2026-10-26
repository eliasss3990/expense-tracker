package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.catalog.CatalogUsage
import com.eliasgonzalez.expensetracker.domain.repository.CatalogRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Solo borra de verdad una entrada SIN datos asociados. Con datos que la
 * referencian responde IN_USE y la UI ofrece desactivarla en su lugar:
 * los gastos viejos tienen que seguir mostrandose igual que siempre.
 *
 * `alsoLock` son los mutex de los casos de uso que ESCRIBEN datos que
 * referencian la entrada (gastos y candidatos). Se toman despues del propio,
 * siempre en este orden: catalogo, candidatos, gastos (el mismo sentido en
 * que ConfirmCandidate toma candidatos y luego RegisterExpense toma gastos,
 * asi que no hay interbloqueo). Sin esto "revisar si esta en uso" y "borrar"
 * no son atomicos frente a un gasto o candidato que se esta guardando justo
 * ahora, y quedaria un dato apuntando a una categoria borrada.
 *
 * `awaitReady` devuelve false si algun repositorio no cargo bien: con las
 * listas vacias "en uso" daria false para datos reales y se borraria de
 * mas, asi que en ese caso no se borra nada.
 */
class DeleteCatalogEntry(
    private val catalog: CatalogRepository,
    private val usage: CatalogUsage,
    private val mutex: Mutex = Mutex(),
    private val awaitReady: suspend () -> Boolean = { true },
    private val alsoLock: List<Mutex> = emptyList(),
) {
    suspend operator fun invoke(kind: String, id: String): CatalogOutcome {
        // Se espera ANTES de tomar los mutex: si la carga tarda, no deja bloqueadas
        // las altas de gastos y candidatos (ni la ruta de notificaciones) mientras tanto.
        if (!awaitReady()) return CatalogOutcome.Rejected(CatalogRejection.NOT_READY)
        return mutex.withLock { withAll(alsoLock) { decide(kind, id) } }
    }

    private suspend fun decide(kind: String, id: String): CatalogOutcome {
        val entry = catalog.find(kind, id)
            ?: return CatalogOutcome.Rejected(CatalogRejection.NOT_FOUND)
        if (entry.protected) return CatalogOutcome.Rejected(CatalogRejection.PROTECTED)
        if (usage.isInUse(kind, id)) return CatalogOutcome.Rejected(CatalogRejection.IN_USE)

        catalog.delete(kind, id)
        return CatalogOutcome.Success()
    }

    private suspend fun withAll(locks: List<Mutex>, block: suspend () -> CatalogOutcome): CatalogOutcome =
        if (locks.isEmpty()) block() else locks.first().withLock { withAll(locks.drop(1), block) }
}
