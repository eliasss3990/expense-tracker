package com.eliasgonzalez.expensetracker.domain.model

enum class CandidateStatus { PENDING, ACCEPTED, EDITED, REJECTED }

/**
 * "La aplicacion cree que detecto un gasto" - nunca es un gasto confirmado.
 * Separar esto de Expense es la pieza central de toda la arquitectura:
 * permite ser agresivo detectando pero conservador al escribir datos
 * financieros reales.
 */
data class ExpenseCandidate(
    val id: Long = 0,
    val amount: Long,
    val currency: String = "PYG",
    val merchant: String,
    val categorySuggestion: String = Category.OTHER.id,
    val occurredAt: Long,
    val detectedAt: Long,
    val sourceType: ExpenseSource,
    val sourceApp: String? = null,
    val parserId: String? = null,
    val confidence: Double = 1.0,
    val status: CandidateStatus = CandidateStatus.PENDING,
)
