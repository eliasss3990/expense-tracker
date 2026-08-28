package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * El chequeo de estado + la escritura van dentro de un Mutex (mismo
 * motivo que CreateCandidate/ConfirmCandidate): sin esto, dos rechazos
 * casi simultaneos del mismo candidato leen PENDING antes de que
 * cualquiera de los dos alcance a marcarlo REJECTED, y quedan dos
 * entradas de actividad para la misma accion.
 */
class RejectCandidate(
    private val candidates: CandidateRepository,
    private val activity: ActivityRepository,
) {
    private val mutex = Mutex()

    suspend operator fun invoke(candidateId: Long): Unit = mutex.withLock {
        val candidate = candidates.findById(candidateId) ?: return@withLock
        if (candidate.status != CandidateStatus.PENDING) return@withLock

        candidates.update(candidate.copy(status = CandidateStatus.REJECTED))
        activity.record(
            ActivityEntry(
                type = ActivityType.CANDIDATE_REJECTED,
                candidateId = candidateId,
                timestamp = System.currentTimeMillis(),
                summary = "${candidate.merchant} — ₲${"%,d".format(candidate.amount)}",
            )
        )
    }
}
