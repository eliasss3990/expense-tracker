package com.eliasgonzalez.expensetracker.domain.analysis

/**
 * Snapshot minimo de la notificacion. No depende de Android ni del paquete notification.
 * El contenido se conserva localmente para auditoria y reintentos del analisis.
 */
data class NotificationSnapshot(
    val packageName: String,
    val applicationName: String,
    val title: String,
    val text: String,
    val bigText: String,
    val timestamp: Long,
) {
    fun searchableText(): String = listOf(title, text, bigText)
        .filter(String::isNotBlank)
        .joinToString("\n")
}

data class ParserSnapshot(
    val amount: Long,
    val currency: String,
    val merchant: String,
    val merchantConfident: Boolean,
    val parserId: String,
    val confidence: Double = 1.0,
)

enum class DetectedMovement {
    EXPENSE,
    OUTGOING_TRANSFER,
    INCOME,
    REFUND,
    NON_TRANSACTION,
    UNKNOWN,
}

/**
 * Cada evidencia debe ser una cita literal de la notificacion, no una explicacion
 * generada por el modelo. La capa de dominio comprueba que exista en el texto original.
 */
data class TransactionExtraction(
    val movement: DetectedMovement,
    val amount: Long? = null,
    val currency: String? = null,
    val merchant: String? = null,
    val movementEvidence: String? = null,
    val amountEvidence: String? = null,
    val merchantEvidence: String? = null,
    val currencyEvidence: String? = null,
)

data class TransactionAnalysisInput(
    val notification: NotificationSnapshot,
    val parserResult: ParserSnapshot?,
)

data class TransactionAnalysisResult(
    val extraction: TransactionExtraction,
    val modelVersion: String,
)

/** Puerto de dominio. La implementacion concreta del modelo vive en data/. */
fun interface TransactionAnalyzer {
    suspend fun analyze(input: TransactionAnalysisInput): TransactionAnalysisResult
}

data class NotificationAnalysisQueueItem(
    val id: Long,
    val input: TransactionAnalysisInput,
    val attempts: Int,
)

data class AnalysisQueueMetrics(
    val pending: Long,
    val processing: Long,
    val completed: Long,
    val fallback: Long,
    val failed: Long,
    val autoRegisterSuggested: Long,
    val reviewSuggested: Long,
    val rejected: Long,
)
