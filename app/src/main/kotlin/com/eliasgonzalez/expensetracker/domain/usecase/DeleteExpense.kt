package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository
import kotlinx.coroutines.sync.Mutex

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
    suspend operator fun invoke(expenseId: Long): Unit = mutex.withCommitLock { deleteOne(expenseId) }

    /** El lote entero es una unidad: salir del Dashboard a mitad de un borrado multiple no lo deja a medias. */
    suspend fun many(expenseIds: Collection<Long>): Unit =
        mutex.withCommitLock { expenseIds.forEach { deleteOne(it) } }

    private suspend fun deleteOne(expenseId: Long) {
        val expense = expenses.findById(expenseId) ?: return
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
}
