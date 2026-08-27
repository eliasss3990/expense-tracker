package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository

/**
 * Un unico caso de uso para registro manual, Quick Tile y confirmacion de
 * candidatos - la unica diferencia entre esos flujos es el campo `source`
 * del Expense, nunca la logica.
 */
class RegisterExpense(
    private val expenses: ExpenseRepository,
    private val activity: ActivityRepository,
) {
    suspend operator fun invoke(expense: Expense): Long {
        val id = expenses.save(expense)
        activity.record(
            ActivityEntry(
                type = ActivityType.EXPENSE_CREATED,
                expenseId = id,
                timestamp = expense.createdAt,
                summary = "${expense.merchant} — ₲${"%,d".format(expense.amount)}",
            )
        )
        return id
    }
}
