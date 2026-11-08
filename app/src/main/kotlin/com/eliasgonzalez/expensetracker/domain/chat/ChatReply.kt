package com.eliasgonzalez.expensetracker.domain.chat

import com.eliasgonzalez.expensetracker.domain.model.Expense
import java.time.LocalDate

/** Lo que la app EJECUTARIA si el usuario confirma. Se muestra completo antes de tocar nada. */
sealed interface ChatProposal {
    data class Create(val expense: Expense) : ChatProposal
    data class Edit(val before: Expense, val after: Expense) : ChatProposal
    data class Delete(val expense: Expense) : ChatProposal
}

/** Una linea con lo que haria la propuesta; la usan el contexto del modelo y los logs, no la UI. */
fun ChatProposal.summary(): String = when (this) {
    is ChatProposal.Create -> "cargar ${expense.merchant} ${expense.amount} en ${expense.categoryId}"
    is ChatProposal.Edit -> "editar ${before.merchant}: monto ${before.amount} a ${after.amount}, categoria ${before.categoryId} a ${after.categoryId}, comercio ${before.merchant} a ${after.merchant}"
    is ChatProposal.Delete -> "borrar ${expense.merchant} ${expense.amount}"
}

enum class ClarifyReason {
    NOT_FOUND, AMBIGUOUS_REFERENCE, INVALID_AMOUNT, INVALID_DATE, UNKNOWN_CATEGORY, DATE_NOT_EDITABLE,
    INVALID_RANGE, UNREADABLE, EMPTY_PHRASE, NOTHING_TO_REVISE,
}

/** Respuesta estructurada: los textos los arma la UI; el dominio solo entrega hechos. */
sealed interface ChatReply {
    data class Summary(
        val metric: ChatMetric, val total: Long, val count: Int,
        val from: LocalDate?, val to: LocalDate?, val categoryId: String?,
    ) : ChatReply

    data class Listing(val items: List<Expense>, val totalCount: Int, val total: Long) : ChatReply
    data class Detail(val expense: Expense) : ChatReply
    data class Confirm(val proposal: ChatProposal) : ChatReply
    data class Clarify(val reason: ClarifyReason, val detail: String? = null) : ChatReply
    data class Talk(val text: String) : ChatReply
    data object Cancelled : ChatReply
}

enum class ConfirmResult { DONE, STALE, FAILED }
