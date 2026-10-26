package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.notification.CandidateNotifier
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import kotlinx.coroutines.sync.Mutex

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
    private val notifier: CandidateNotifier = CandidateNotifier.None,
    private val awaitReady: suspend () -> Boolean = { true },
) {
    suspend operator fun invoke(candidateId: Long): Unit {
        // Ver el comentario equivalente en ConfirmCandidate.
        if (!awaitReady()) return
        return mutex.withCommitLock {
            val candidate = candidates.findById(candidateId)
            if (candidate == null || candidate.status != CandidateStatus.PENDING) {
                notifier.dismiss(candidateId)
                return@withCommitLock
            }

            candidates.update(candidate.copy(status = CandidateStatus.REJECTED))
            activity.record(
                ActivityEntry(
                    type = ActivityType.CANDIDATE_REJECTED,
                    candidateId = candidateId,
                    timestamp = System.currentTimeMillis(),
                    summary = "${candidate.merchant} — ₲${"%,d".format(candidate.amount)}",
                )
            )
            notifier.dismiss(candidateId)
        }
    }
}
