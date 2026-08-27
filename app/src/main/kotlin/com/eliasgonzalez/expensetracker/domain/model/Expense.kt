package com.eliasgonzalez.expensetracker.domain.model

enum class ExpenseSource { MANUAL, QUICK_TILE, NOTIFICATION }

/**
 * Un gasto confirmado: dinero que ya forma parte de las estadisticas.
 * Nunca se crea directo desde un parser o notificacion - siempre pasa por
 * un ExpenseCandidate primero (ver domain/model/ExpenseCandidate.kt).
 */
data class Expense(
    val id: Long = 0,
    val amount: Long,
    val currency: String = "PYG",
    val merchant: String,
    val categoryId: String = Category.OTHER.id,
    val description: String = "",
    val occurredAt: Long,
    val createdAt: Long,
    val source: ExpenseSource,
    val sourceReference: Long? = null,
)
