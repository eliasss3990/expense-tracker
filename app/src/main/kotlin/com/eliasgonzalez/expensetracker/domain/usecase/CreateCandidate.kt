package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

private const val DEDUP_WINDOW_MILLIS = 5 * 60 * 1000L

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
 */
class CreateCandidate(
    private val candidates: CandidateRepository,
    private val activity: ActivityRepository,
    private val mutex: Mutex = Mutex(),
) {
    suspend operator fun invoke(candidate: ExpenseCandidate): Long? = mutex.withLock {
        if (findDuplicate(candidate) != null) return@withLock null

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

    // Solo compara contra candidatos que sigan PENDING: uno ya
    // ACCEPTED/EDITED/REJECTED queda en la lista para siempre (no se
    // borra) y sin este filtro un candidato ya resuelto seguia
    // "bloqueando" como duplicado a una compra genuinamente distinta y
    // posterior con el mismo monto+comercio (ej. dos cafes identicos en
    // el mismo lugar el mismo dia) - el segundo gasto real desaparecia
    // en silencio, sin ningun error ni aviso.
    private fun findDuplicate(candidate: ExpenseCandidate): ExpenseCandidate? =
        candidates.candidates.value.find { existing ->
            val merchantOk = if (existing.merchantConfident && candidate.merchantConfident) {
                existing.merchant.normalizedForMatch() == candidate.merchant.normalizedForMatch()
            } else {
                true
            }
            existing.status == CandidateStatus.PENDING &&
                existing.amount == candidate.amount &&
                existing.currency == candidate.currency &&
                merchantOk &&
                abs(existing.detectedAt - candidate.detectedAt) <= DEDUP_WINDOW_MILLIS
        }

    private fun String.normalizedForMatch(): String =
        trim().uppercase().filter { it.isLetterOrDigit() }
}
