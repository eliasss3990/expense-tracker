package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * El chequeo de estado + la escritura van dentro de un Mutex COMPARTIDO
 * con CreateCandidate/ConfirmCandidate/RejectCandidate (ver el
 * comentario en CreateCandidate.kt): sin esto, dos acciones casi
 * simultaneas sobre el mismo candidato (ej. editar y confirmar el mismo
 * candidato a la vez desde dos lugares) leen PENDING antes de que
 * cualquiera alcance a cambiarle el estado, y mas de una termina
 * registrando un Expense.
 */
class EditCandidate(
    private val candidates: CandidateRepository,
    private val registerExpense: RegisterExpense,
    private val activity: ActivityRepository,
    private val mutex: Mutex = Mutex(),
) {
    suspend operator fun invoke(
        candidateId: Long,
        amount: Long,
        merchant: String,
        categoryId: String = "",
    ): Long? = mutex.withLock {
        val candidate = candidates.findById(candidateId) ?: return@withLock null
        if (candidate.status != CandidateStatus.PENDING) return@withLock null

        val now = System.currentTimeMillis()
        val finalCategoryId = categoryId.ifBlank { candidate.categorySuggestion }
        val expenseId = registerExpense(
            Expense(
                amount = amount,
                currency = candidate.currency,
                merchant = merchant,
                categoryId = finalCategoryId,
                occurredAt = candidate.occurredAt,
                createdAt = now,
                source = candidate.sourceType,
                sourceReference = candidate.id,
            )
        )
        candidates.update(
            candidate.copy(
                status = CandidateStatus.EDITED,
                amount = amount,
                merchant = merchant,
                categorySuggestion = finalCategoryId,
            )
        )
        activity.record(
            ActivityEntry(
                type = ActivityType.CANDIDATE_EDITED,
                expenseId = expenseId,
                candidateId = candidateId,
                timestamp = now,
                summary = "$merchant — ₲${"%,d".format(amount)}",
            )
        )
        return expenseId
    }
}
