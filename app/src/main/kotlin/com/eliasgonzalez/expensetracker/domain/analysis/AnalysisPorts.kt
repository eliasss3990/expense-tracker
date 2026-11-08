package com.eliasgonzalez.expensetracker.domain.analysis

import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate

/** Un aviso a analizar. [key] es una huella estable del evento (no contiene el texto). */
data class AnalysisEvent(
    val key: String,
    val notification: NotificationSnapshot,
    val parser: ParserEvidence,
    val trust: SourceTrust,
    /**
     * true si el flujo de siempre ya creo el candidato al llegar el aviso (modo Sombra o sin modelo). Si es false, el
     * analisis es la UNICA via: aunque el usuario cambie de modo antes de procesarlo, el aviso no puede perderse.
     */
    val legacyHandled: Boolean = false,
)

data class QueuedAnalysis(val id: Long, val event: AnalysisEvent, val attempts: Int)

/** Cola persistente de avisos pendientes de analisis. No conoce a ningun modelo. */
interface AnalysisQueueRepository {
    /** null si el mismo evento ya estaba encolado (idempotente por [AnalysisEvent.key]). */
    suspend fun enqueue(event: AnalysisEvent): Long?

    /** El mas antiguo pendiente con menos de [maxAttempts] intentos, fuera de [excluded]. */
    suspend fun nextPending(excluded: Set<Long>, maxAttempts: Int): QueuedAnalysis?

    /**
     * Un pendiente que ya agoto sus intentos (por fallos, o porque el proceso murio durante el analisis
     * [maxAttempts] veces). Sin esto quedaria PENDING para siempre: nextPending lo excluye y nada lo cierra.
     */
    suspend fun nextExhausted(maxAttempts: Int): QueuedAnalysis?

    /** Pasa a PROCESSING e incrementa los intentos; false si otro lo tomo antes. */
    suspend fun markProcessing(id: Long): Boolean
    suspend fun markDone(id: Long)

    /**
     * Vuelve a PENDING para un reintento posterior. Con [refundAttempt] el intento no cuenta
     * (ej. la base aun no cargo: no es culpa del aviso).
     */
    suspend fun markRetry(id: Long, errorClass: String, refundAttempt: Boolean = false)
    suspend fun markGaveUp(id: Long, errorClass: String)

    /** Los PROCESSING que quedaron por la muerte del proceso vuelven a PENDING. */
    suspend fun recoverProcessing()

    /** Borra lo terminado antes de [timestamp] (el texto de las notificaciones no se guarda para siempre). */
    suspend fun pruneBefore(timestamp: Long)
}

/** Avisa que hay un aviso financiero que no se pudo interpretar (sin importe utilizable): que el usuario no lo pierda. */
fun interface ReviewNoticeAnnouncer {
    suspend fun announce(sourceApp: String)
}

/** Si hay un modelo utilizable ahora mismo (instalado y verificado). */
fun interface ModelAvailability {
    fun isReady(): Boolean
}

/** Avisa al usuario de un candidato nuevo. La implementacion Android publica la notificacion. */
fun interface CandidateAnnouncer {
    suspend fun announce(candidate: ExpenseCandidate)
}
