package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.catalog.CategoryPolicy
import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.notification.CandidateNotifier
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
    private val notifier: CandidateNotifier = CandidateNotifier.None,
    private val categoryPolicy: CategoryPolicy = CategoryPolicy.None,
    private val awaitReady: suspend () -> Boolean = { true },
) {
    suspend operator fun invoke(
        candidateId: Long,
        amount: Long,
        merchant: String,
        categoryId: String = "",
        description: String = "",
    ): Long? = mutex.withLock {
        // Ver el comentario equivalente en ConfirmCandidate.
        if (!awaitReady()) return@withLock null
        val candidate = candidates.findById(candidateId)
        if (candidate == null || candidate.status != CandidateStatus.PENDING) {
            // Ya resuelto (o inexistente): igual se retira la notificacion, por si
            // quedo una huerfana de una version anterior o de una carrera.
            notifier.dismiss(candidateId)
            return@withLock null
        }

        val now = System.currentTimeMillis()
        // Se resuelve ANTES de guardar el candidato para que quede igual que
        // el gasto que se registra (RegisterExpense aplica la misma politica).
        val finalCategoryId = categoryPolicy.forNewExpense(
            categoryId.ifBlank { candidate.categorySuggestion }
        )
        val expenseId = registerExpense(
            Expense(
                amount = amount,
                currency = candidate.currency,
                merchant = merchant,
                categoryId = finalCategoryId,
                description = description,
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
                description = description,
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
        notifier.dismiss(candidateId)
        return expenseId
    }
}
