package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Un unico caso de uso para registro manual, Quick Tile y confirmacion de
 * candidatos - la unica diferencia entre esos flujos es el campo `source`
 * del Expense, nunca la logica.
 *
 * Comparte un Mutex con EditExpense/DeleteExpense (inyectado desde
 * AppContainer) - ver el comentario en DeleteExpense.kt para el motivo.
 */
class RegisterExpense(
    private val expenses: ExpenseRepository,
    private val activity: ActivityRepository,
    private val mutex: Mutex = Mutex(),
) {
    suspend operator fun invoke(expense: Expense): Long = mutex.withLock {
        val id = expenses.save(expense)
        activity.record(
            ActivityEntry(
                type = ActivityType.EXPENSE_CREATED,
                expenseId = id,
                timestamp = expense.createdAt,
                summary = "${expense.merchant} — ₲${"%,d".format(expense.amount)}",
            )
        )
        id
    }
}
