package com.eliasgonzalez.expensetracker.domain.model

enum class ActivityType {
    EXPENSE_CREATED,
    EXPENSE_EDITED,
    EXPENSE_DELETED,
    CANDIDATE_CREATED,
    CANDIDATE_ACCEPTED,
    CANDIDATE_EDITED,
    CANDIDATE_REJECTED,
}

/**
 * Traza de auditoria. Sin esto, un Expense
 * simplemente "cambia" sin dejar rastro de por que.
 */
data class ActivityEntry(
    val id: Long = 0,
    val type: ActivityType,
    val expenseId: Long? = null,
    val candidateId: Long? = null,
    val timestamp: Long,
    val summary: String,
)
