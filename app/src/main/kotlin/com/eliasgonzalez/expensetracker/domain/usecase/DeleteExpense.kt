package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Comparte un Mutex con RegisterExpense/EditExpense (inyectado desde
 * AppContainer, no uno propio): sin esto, si este borrado corre justo
 * entre el findById y el update de una EditExpense sobre el mismo
 * gasto, la edicion queda como no-op silencioso (0 filas afectadas) con
 * una entrada de actividad huerfana. El mutex hace que las dos
 * operaciones (edicion completa, borrado completo) sean atomicas entre
 * si, no solo dentro de cada una.
 */
class DeleteExpense(
    private val expenses: ExpenseRepository,
    private val activity: ActivityRepository,
    private val mutex: Mutex = Mutex(),
) {
    suspend operator fun invoke(expenseId: Long): Unit = mutex.withLock {
        val expense = expenses.findById(expenseId) ?: return@withLock
        expenses.delete(expenseId)
        activity.record(
            ActivityEntry(
                type = ActivityType.EXPENSE_DELETED,
                expenseId = expenseId,
                timestamp = System.currentTimeMillis(),
                summary = "${expense.merchant} — ₲${"%,d".format(expense.amount)}",
            )
        )
    }

    suspend fun many(expenseIds: Collection<Long>) {
        expenseIds.forEach { invoke(it) }
    }
}
