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
    val description: String = "",
    val occurredAt: Long,
    val detectedAt: Long,
    val sourceType: ExpenseSource,
    val sourceApp: String? = null,
    val parserId: String? = null,
    val confidence: Double = 1.0,
    val status: CandidateStatus = CandidateStatus.PENDING,
    // false cuando el parser no encontró un comercio/contraparte real y
    // usó un valor de relleno (ej. el nombre de la app) - la deduplicación
    // no exige que el comercio coincida entre dos candidatos si alguno de
    // los dos no es confiable, porque la misma transacción puede llegar
    // con datos mas ricos desde otra fuente (ej. el mail del banco trae
    // el beneficiario real, la notificación push de la app no).
    val merchantConfident: Boolean = true,
)
