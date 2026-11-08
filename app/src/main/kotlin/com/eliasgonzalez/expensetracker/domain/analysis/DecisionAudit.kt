package com.eliasgonzalez.expensetracker.domain.analysis

/** Que se hizo finalmente con el aviso. */
enum class DecisionEffect {
    AUTO_REGISTERED,
    CANDIDATE_CREATED,

    /** Ya existia un gasto o candidato equivalente. */
    DUPLICATE,
    DISCARDED,

    /** Modo Sombra: el modelo solo midio, el flujo visible fue el de siempre. */
    SHADOW_ONLY,

    /** El modelo no pudo analizar y se uso el parser (candidato de siempre). */
    FALLBACK_LEGACY,

    /** La interpretacion no dio un importe utilizable ni habia parser: queda solo en la auditoria. */
    UNUSABLE,

    /** Sin importe utilizable y la fuente no es un banco (p. ej. un correo): se ignora sin molestar al usuario. */
    IGNORED_UNREADABLE,
    GAVE_UP,
}

/**
 * Registro de POR QUE un aviso termino como termino: sirve para responder, sin reproducir
 * nada en vivo, "por que se registro solo" o "por que salio como candidato". Se persiste (logcat
 * se pierde al reiniciar). Guarda la interpretacion y los codigos de decision; el texto original
 * vive en la cola, con su propia retencion.
 */
data class DecisionAudit(
    val eventKey: String,
    val decidedAt: Long,
    val mode: AnalysisMode,
    val sourceApp: String,
    val outcome: RegistrationOutcome?,
    val reasons: Set<DecisionReason>,
    val effect: DecisionEffect,
    val expenseId: Long?,
    val candidateId: Long?,
    val interpretations: List<InterpretationResult>,
    /** Clase del error cuando no hubo interpretacion (sin mensajes: pueden llevar datos). */
    val errorClass: String? = null,
    /** Categoria asignada y de donde salio (null si el aviso no llego a registrarse ni a candidato). */
    val categoryId: String? = null,
    val categorySource: CategorySource? = null,
)

interface DecisionAuditRepository {
    suspend fun record(audit: DecisionAudit)
    suspend fun recent(limit: Int): List<DecisionAudit>
    suspend fun pruneBefore(timestamp: Long)
}
