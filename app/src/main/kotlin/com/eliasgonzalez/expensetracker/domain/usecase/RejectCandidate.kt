package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * El chequeo de estado + la escritura van dentro de un Mutex COMPARTIDO
 * con CreateCandidate/ConfirmCandidate/EditCandidate (ver el comentario
 * en CreateCandidate.kt): sin esto, dos acciones casi simultaneas sobre
 * el mismo candidato leen PENDING antes de que cualquiera alcance a
 * cambiarle el estado.
 */
class RejectCandidate(
    private val candidates: CandidateRepository,
    private val activity: ActivityRepository,
    private val mutex: Mutex = Mutex(),
) {
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
