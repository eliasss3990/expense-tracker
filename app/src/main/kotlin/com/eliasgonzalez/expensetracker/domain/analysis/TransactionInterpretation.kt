package com.eliasgonzalez.expensetracker.domain.analysis

/**
 * Interpretacion de una notificacion, independiente de QUE modelo la produjo. El modelo
 * rellena campos cerrados (no citas para validar con regex): entiende la semantica y el
 * dominio solo comprueba que lo dicho sea consistente. Cambiar de modelo es implementar
 * [TransactionInterpreter]; nada mas de esta capa conoce al modelo.
 */
enum class MoneyDirection { OUTGOING, INCOMING, REFUND, NONE, UNKNOWN }

enum class OperationStatus { COMPLETED, PENDING, DECLINED, REVERSED, UNKNOWN }

/** Papel de cada cifra que aparece en el texto: solo OPERATION es el importe del movimiento. */
enum class AmountRole { OPERATION, BALANCE, LIMIT, INSTALLMENT, FEE, OTHER }

/** Dudas que el propio modelo declara; cualquiera lleva el caso a revision. */
enum class ModelDoubt { AMBIGUOUS_AMOUNT, AMBIGUOUS_DIRECTION, AMBIGUOUS_STATUS, UNREADABLE, OTHER }

data class AmountMention(val value: Long, val role: AmountRole, val currency: String?)

data class TransactionInterpretation(
    val direction: MoneyDirection,
    val status: OperationStatus,
    val amounts: List<AmountMention>,
    val merchant: String?,
    val doubts: Set<ModelDoubt> = emptySet(),
) {
    /** El unico importe con rol OPERATION, o null si no hay exactamente uno. */
    val operationAmount: AmountMention?
        get() = amounts.filter { it.role == AmountRole.OPERATION }.singleOrNull()
}

/** Pasadas independientes: la segunda re-extrae con otro enfoque para contrastar la primera. */
enum class InterpretationPass { PRIMARY, CROSS_CHECK }

data class InterpretationRequest(
    val notification: NotificationSnapshot,
    val parser: ParserEvidence,
    val pass: InterpretationPass,
)

data class InterpretationResult(
    val interpretation: TransactionInterpretation,
    val pass: InterpretationPass,
    /** Identificador estable del modelo (ej. "gemma-4-e2b-it"), para auditar y comparar. */
    val modelId: String,
    val durationMillis: Long,
)

/** Puerto de dominio: el unico punto de contacto con cualquier modelo. Lanza si falla. */
fun interface TransactionInterpreter {
    suspend fun interpret(request: InterpretationRequest): InterpretationResult
}
