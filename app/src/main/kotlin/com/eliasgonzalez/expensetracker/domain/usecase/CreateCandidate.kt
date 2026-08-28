package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
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
 * El chequeo+guardado corre bajo un mutex: dos notificaciones casi
 * simultáneas (llegan con milisegundos de diferencia, algo común cuando
 * el banco y el mail avisan "a la vez") podían leer el estado antes de
 * que la primera terminara de guardar y las dos pasaban el chequeo de
 * duplicado - una condición de carrera real, no solo teórica.
 */
class CreateCandidate(
    private val candidates: CandidateRepository,
    private val activity: ActivityRepository,
) {
    private val mutex = Mutex()

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

    private fun findDuplicate(candidate: ExpenseCandidate): ExpenseCandidate? =
        candidates.candidates.value.find { existing ->
            val merchantOk = if (existing.merchantConfident && candidate.merchantConfident) {
                existing.merchant.normalizedForMatch() == candidate.merchant.normalizedForMatch()
            } else {
                true
            }
            existing.amount == candidate.amount &&
                existing.currency == candidate.currency &&
                merchantOk &&
                abs(existing.detectedAt - candidate.detectedAt) <= DEDUP_WINDOW_MILLIS
        }

    private fun String.normalizedForMatch(): String =
        trim().uppercase().filter { it.isLetterOrDigit() }
}
