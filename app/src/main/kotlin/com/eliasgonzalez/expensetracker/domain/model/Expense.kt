package com.eliasgonzalez.expensetracker.domain.model

enum class ExpenseSource { MANUAL, QUICK_TILE, NOTIFICATION }

/**
 * Un gasto confirmado: dinero que ya forma parte de las estadisticas.
 * Puede crearse desde un candidato confirmado o mediante auto-registro IA cuando
 * la reconciliacion deterministica no encuentra contradicciones y la politica lo permite.
 */
data class Expense(
    val id: Long = 0,
    val amount: Long,
    val currency: String = "PYG",
    val merchant: String,
    val categoryId: String = CategoryIds.OTHER,
    val description: String = "",
    val occurredAt: Long,
    val createdAt: Long,
    val source: ExpenseSource,
    val sourceReference: Long? = null,
)
