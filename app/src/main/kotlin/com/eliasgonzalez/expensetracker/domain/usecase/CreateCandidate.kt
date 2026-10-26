package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.catalog.CategoryPolicy
import com.eliasgonzalez.expensetracker.domain.log.AppLog
import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import kotlinx.coroutines.sync.Mutex
import kotlin.math.abs

internal const val DEDUP_WINDOW_MILLIS = 5 * 60 * 1000L

/**
 * Contra candidatos YA RESUELTOS (aceptados, editados o rechazados) la ventana es mas corta
 * que contra los pendientes: el segundo aviso de una misma compra (otro canal, otro mail del
 * banco) suele llegar en segundos, y si el usuario ya resolvio el primero no hay un pendiente
 * contra el cual compararlo. Una ventana larga volveria a perder compras reales repetidas
 * (dos cafes iguales), por eso es corta.
 */
internal const val DEDUP_RESOLVED_WINDOW_MILLIS = 60 * 1000L

private const val TAG = "CreateCandidate"

/**
 * Crea un candidato, salvo que ya exista uno muy parecido detectado hace
 * poco - eso pasa cuando la misma compra dispara notificaciones de más de
 * una fuente (ej. el banco y su mail de confirmación casi al mismo
 * tiempo). No borra información, simplemente no duplica el candidato:
 * devuelve null y el llamador no debería mostrar una segunda notificación.
 *
 * El comercio solo se exige igual entre ambos candidatos si los dos
 * confían en su propio dato de comercio (`merchantConfident`) - una
 * fuente puede traer el nombre real de la contraparte y otra solo un
 * relleno genérico (ver UenoBankParser), y siguen siendo la misma
 * transacción.
 *
 * El chequeo+guardado corre bajo un mutex COMPARTIDO con
 * ConfirmCandidate/EditCandidate/RejectCandidate (inyectado desde
 * AppContainer, no uno propio por clase): dos notificaciones casi
 * simultáneas (llegan con milisegundos de diferencia, algo común cuando
 * el banco y el mail avisan "a la vez") podían leer el estado antes de
 * que la primera terminara de guardar y las dos pasaban el chequeo de
 * duplicado - una condición de carrera real, no solo teórica. Un mutex
 * propio por clase solo serializaba llamadas repetidas al MISMO caso de
 * uso; no impedía que, por ejemplo, ConfirmCandidate y EditCandidate
 * operaran sobre el mismo candidato al mismo tiempo y duplicaran el
 * Expense - de ahí que las cuatro clases compartan una sola instancia.
 *
 * El bloque corre bajo `withCommitLock` (ver CommitLock.kt): no se cancela a mitad, y
 * `awaitReady` se espera antes de tomar el lock.
 */
class CreateCandidate(
    private val candidates: CandidateRepository,
    private val activity: ActivityRepository,
    private val mutex: Mutex = Mutex(),
    private val categoryPolicy: CategoryPolicy = CategoryPolicy.None,
    private val awaitReady: suspend () -> Boolean = { true },
) {
    suspend operator fun invoke(original: ExpenseCandidate): Long? {
        // Esperar la carga antes de buscar duplicados (con la lista vacia ninguno se
        // detectaria). Si la carga fallo se sigue igual: un duplicado es mejor que
        // perder un gasto detectado. Va FUERA del lock: ver CommitLock.
        awaitReady()
        return mutex.withCommitLock {
            val duplicate = findDuplicate(original)
            if (duplicate != null) {
                // Sin montos ni comercios (datos financieros): solo la forma de la decision.
                AppLog.i(
                    TAG,
                    "Duplicado descartado: previo=${duplicate.status} " +
                        "deltaMs=${abs(duplicate.detectedAt - original.detectedAt)} " +
                        "mismaApp=${duplicate.sourceApp == original.sourceApp}",
                )
                return@withCommitLock null
            }

            // Un parser puede sugerir una categoria que el usuario ya elimino:
            // se guarda lo que realmente existe, no un id colgado.
            val candidate = original.copy(
                categorySuggestion = categoryPolicy.forNewExpense(original.categorySuggestion)
            )
            val id = candidates.save(candidate)
            activity.record(
                ActivityEntry(
                    type = ActivityType.CANDIDATE_CREATED,
                    candidateId = id,
                    timestamp = candidate.detectedAt,
                    summary = "${candidate.merchant} — ₲${"%,d".format(candidate.amount)}",
                )
            )
            id
        }
    }

    // Pendientes: ventana de 5 minutos. Resueltos (ACCEPTED/EDITED/REJECTED, que nunca se
    // borran y quedan en la lista para siempre): solo ventana corta
    // (DEDUP_RESOLVED_WINDOW_MILLIS). Sin limite de ventana, un resuelto "bloqueaba" como
    // duplicado a una compra genuinamente distinta y posterior con el mismo monto+comercio
    // (dos cafes iguales el mismo dia), y el segundo gasto real desaparecia en silencio;
    // sin comparar contra resueltos, el segundo aviso de la MISMA compra, llegado despues de
    // aceptar/rechazar el primero, volvia a sugerirse. Los gastos cargados a mano no se
    // comparan.
    private fun findDuplicate(candidate: ExpenseCandidate): ExpenseCandidate? =
        candidates.candidates.value.find { existing ->
            val window = if (existing.status == CandidateStatus.PENDING) {
                DEDUP_WINDOW_MILLIS
            } else {
                DEDUP_RESOLVED_WINDOW_MILLIS
            }
            // Lo barato primero: la lista incluye todos los candidatos historicos y
            // normalizar comercios (alocaciones) solo vale la pena si lo demas coincide.
            existing.amount == candidate.amount &&
                existing.currency == candidate.currency &&
                abs(existing.detectedAt - candidate.detectedAt) <= window &&
                merchantsMatch(existing, candidate)
        }

    private fun merchantsMatch(a: ExpenseCandidate, b: ExpenseCandidate): Boolean =
        if (a.merchantConfident && b.merchantConfident) {
            a.merchant.normalizedForMatch() == b.merchant.normalizedForMatch()
        } else {
            true
        }

    private fun String.normalizedForMatch(): String =
        trim().uppercase().filter { it.isLetterOrDigit() }
}
