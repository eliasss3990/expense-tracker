package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository

class EditExpense(
    private val expenses: ExpenseRepository,
    private val activity: ActivityRepository,
) {
    suspend operator fun invoke(
        expenseId: Long,
        amount: Long,
        merchant: String,
        categoryId: String,
    ) {
        val expense = expenses.findById(expenseId) ?: return
        expenses.update(expense.copy(amount = amount, merchant = merchant, categoryId = categoryId))
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
