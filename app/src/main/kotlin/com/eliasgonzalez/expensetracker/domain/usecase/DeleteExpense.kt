package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository

class DeleteExpense(
    private val expenses: ExpenseRepository,
    private val activity: ActivityRepository,
) {
    suspend operator fun invoke(expenseId: Long) {
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

    suspend fun many(expenseIds: Collection<Long>) {
        expenseIds.forEach { invoke(it) }
    }
}
