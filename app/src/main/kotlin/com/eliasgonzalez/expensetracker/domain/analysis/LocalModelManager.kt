package com.eliasgonzalez.expensetracker.domain.analysis

enum class ModelInstallStatus { NOT_INSTALLED, QUEUED, DOWNLOADING, PAUSED, DOWNLOADED, VERIFYING, INSTALLED, FAILED }

data class ModelInstallState(
    val status: ModelInstallStatus,
    val progressPercent: Int? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    /** Codigo de motivo del sistema de descarga, solo para diagnostico. */
    val failureReason: Int? = null,
)

/**
 * Instala y verifica el modelo local. La UI y el resto de la app solo conocen este puerto:
 * que el modelo sea Gemma, otro o uno descargado de otro lado es un detalle de la implementacion.
 */
interface LocalModelManager : ModelAvailability {
    val displayName: String
    val approximateSize: String

    fun state(): ModelInstallState

    /** Inicia la descarga por accion explicita del usuario (nunca en segundo plano por si sola). */
    fun startDownload()
    fun cancel()

    /** Si una descarga termino la verifica; devuelve el estado final. Seguro de llamar seguido. */
    suspend fun refresh(): ModelInstallState
}
