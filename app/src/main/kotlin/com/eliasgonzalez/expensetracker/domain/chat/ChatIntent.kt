package com.eliasgonzalez.expensetracker.domain.chat

import com.eliasgonzalez.expensetracker.domain.analysis.CategoryOption
import java.time.LocalDate

enum class ChatMetric { SUM, COUNT, LIST, AVERAGE }

enum class EditableField { AMOUNT, MERCHANT, CATEGORY, DATE }

/** A que gasto se refiere el usuario. Lo resuelve la APP contra sus datos; el modelo nunca ve ni elige ids. */
sealed interface ExpenseRef {
    data object Last : ExpenseRef
    data object MostExpensive : ExpenseRef
    data class ByText(val text: String) : ExpenseRef
}

/**
 * Lo unico que el modelo produce: una INTENCION cerrada, sin ejecutar nada. Cada variante tiene solo los
 * campos que necesita, y ninguna puede apuntar a "todos los gastos": un borrado o edicion siempre es de UN gasto.
 */
sealed interface ChatIntent {
    data class Query(val metric: ChatMetric, val from: LocalDate?, val to: LocalDate?, val categoryId: String?) : ChatIntent
    data class Describe(val ref: ExpenseRef) : ChatIntent
    data class Create(val amount: Long, val merchant: String, val categoryId: String?, val date: LocalDate?) : ChatIntent
    data class Edit(val ref: ExpenseRef, val field: EditableField, val newValue: String) : ChatIntent
    data class Delete(val ref: ExpenseRef) : ChatIntent
    data class SmallTalk(val reply: String) : ChatIntent

    /** Corrige la propuesta pendiente ("no, era 50000"): solo tiene sentido con una propuesta a la vista. */
    data class Revise(val field: EditableField, val newValue: String) : ChatIntent

    /** Abandona la propuesta pendiente ("olvidalo"). */
    data object Cancel : ChatIntent

    /** El modelo fallo o su salida no se pudo leer: nunca se adivina. */
    data class Unreadable(val reason: String) : ChatIntent
}

enum class TurnRole { USER, ASSISTANT }

/** Un mensaje del chat ABIERTO. Vive solo en memoria mientras dure ese chat (ver [ChatSession]). */
data class ChatTurn(val role: TurnRole, val text: String)

/**
 * [history] son los ultimos mensajes de ESTE chat; [pending] la propuesta que el usuario tiene a la vista y aun no
 * confirmo ni cancelo (para que "no, era 50000" se entienda).
 */
data class ChatRequest(
    val phrase: String,
    val today: LocalDate,
    val categories: List<CategoryOption>,
    val history: List<ChatTurn> = emptyList(),
    val pending: ChatProposal? = null,
)

/** Puerto del modelo para el chat: cambiar de modelo o de motor es otra implementacion, no tocar el dominio. */
fun interface ChatInterpreter {
    suspend fun interpret(request: ChatRequest): ChatIntent
}
