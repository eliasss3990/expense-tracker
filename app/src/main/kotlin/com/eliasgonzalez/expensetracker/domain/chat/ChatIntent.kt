package com.eliasgonzalez.expensetracker.domain.chat

import com.eliasgonzalez.expensetracker.domain.analysis.CategoryOption
import java.time.LocalDate

enum class ChatMetric { SUM, COUNT, LIST, AVERAGE }

enum class EditableField { AMOUNT, MERCHANT, DESCRIPTION, CATEGORY, DATE }

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
    data class Query(
        val metric: ChatMetric,
        val period: ChatPeriod,
        /** Solo se usan con [ChatPeriod.CUSTOM]; en los demas periodos las fechas las calcula la app. */
        val from: LocalDate?,
        val to: LocalDate?,
        val categoryId: String?,
    ) : ChatIntent
    data class Describe(val ref: ExpenseRef) : ChatIntent
    /**
     * [merchant] es el LUGAR donde se pago y solo si el usuario lo nombro ("en Shell"); [description] es QUE se compro ("nafta").
     * Mezclarlos (guardar "nafta" como comercio) era el error original.
     */
    data class Create(
        val amount: Long,
        val merchant: String,
        val categoryId: String?,
        val date: LocalDate?,
        val description: String = "",
    ) : ChatIntent
    data class Edit(val ref: ExpenseRef, val field: EditableField, val newValue: String) : ChatIntent
    data class Delete(val ref: ExpenseRef) : ChatIntent
    data class SmallTalk(val reply: String) : ChatIntent

    /** Corrige la propuesta pendiente ("no, era 50000"): solo tiene sentido con una propuesta a la vista. */
    data class Revise(val field: EditableField, val newValue: String) : ChatIntent

    /** Categorias: igual que con los gastos, el modelo solo propone; la app resuelve la categoria por nombre y pide confirmacion. */
    data class CategoryCreate(val name: String) : ChatIntent
    data class CategoryRename(val name: String, val newName: String) : ChatIntent
    data class CategoryDelete(val name: String) : ChatIntent

    /** Corrige el NOMBRE de una propuesta de categoria pendiente ("mejor llamala Mascotas"). */
    data class ReviseName(val newName: String) : ChatIntent

    /** Pregunta por lo que se puede hacer ("que podes hacer", "podes crear categorias?"): la app responde con un texto fijo. */
    data object Help : ChatIntent

    /** Abandona la propuesta pendiente ("olvidalo"). */
    data object Cancel : ChatIntent

    /** El modelo fallo o su salida no se pudo leer: nunca se adivina. */
    data class Unreadable(val reason: String) : ChatIntent
}

/** Lo que abarco la ultima consulta del chat (con las fechas YA resueltas): para que "esos gastos" o "y en comida?" tengan a que referirse. */
data class QueryScope(
    val metric: ChatMetric,
    val period: ChatPeriod,
    val from: LocalDate?,
    val to: LocalDate?,
    val categoryId: String?,
)

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
    /** La ultima consulta de este chat, si hubo una. */
    val lastQuery: QueryScope? = null,
)

/** Puerto del modelo para el chat: cambiar de modelo o de motor es otra implementacion, no tocar el dominio. */
fun interface ChatInterpreter {
    suspend fun interpret(request: ChatRequest): ChatIntent
}
