package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Comparte un Mutex con RegisterExpense/DeleteExpense (inyectado desde
 * AppContainer) - ver el comentario en DeleteExpense.kt para el motivo:
 * sin esto, si un borrado corre justo entre el findById y el update de
 * una edicion sobre el MISMO gasto, la edicion se pierde en silencio
 * (0 filas afectadas, sin error) y queda una entrada de actividad
 * "editado" referenciando un gasto que ya no existe.
 */
class EditExpense(
    private val expenses: ExpenseRepository,
    private val activity: ActivityRepository,
    private val mutex: Mutex = Mutex(),
) {
    suspend operator fun invoke(
        expenseId: Long,
        amount: Long,
        merchant: String,
        categoryId: String,
        description: String? = null,
    ): Unit = mutex.withLock {
        val expense = expenses.findById(expenseId) ?: return@withLock
        expenses.update(
            expense.copy(
                amount = amount,
                merchant = merchant,
                categoryId = categoryId,
                description = description ?: expense.description,
            )
        )
        activity.record(
            ActivityEntry(
                type = ActivityType.EXPENSE_EDITED,
                expenseId = expenseId,
                timestamp = System.currentTimeMillis(),
                summary = "$merchant — ₲${"%,d".format(amount)}",
            )
        )
    }
}
