package com.eliasgonzalez.expensetracker.ui.chat

import com.eliasgonzalez.expensetracker.domain.chat.ChatMetric
import com.eliasgonzalez.expensetracker.domain.chat.ChatProposal
import com.eliasgonzalez.expensetracker.domain.chat.ChatReply
import com.eliasgonzalez.expensetracker.domain.chat.ClarifyReason
import com.eliasgonzalez.expensetracker.domain.chat.ConfirmResult
import com.eliasgonzalez.expensetracker.domain.model.Expense
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Textos del chat. El dominio entrega hechos estructurados; aca se convierten en frases (puro y probado, sin Android). */
internal class ChatPresentation(
    private val categoryLabel: (String) -> String,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val dateTime = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(zone)
    private val day = DateTimeFormatter.ofPattern("dd/MM")

    fun money(amount: Long): String = "₲" + String.format(Locale("es", "PY"), "%,d", amount)

    fun text(reply: ChatReply): String = when (reply) {
        is ChatReply.Summary -> summary(reply)
        is ChatReply.Listing -> listing(reply)
        is ChatReply.Detail -> "${describe(reply.expense)}\nFuente: ${reply.expense.source.name.lowercase()}."
        is ChatReply.Confirm -> proposal(reply.proposal)
        is ChatReply.Clarify -> clarify(reply)
        is ChatReply.Talk -> reply.text.ifBlank { "Decime qué querés consultar o cargar." }
        ChatReply.Cancelled -> "Listo, lo descarté. No se guardó nada."
    }

    fun confirmed(result: ConfirmResult, proposal: ChatProposal): String = when (result) {
        ConfirmResult.DONE -> when (proposal) {
            is ChatProposal.Create -> "Listo, cargué ${proposal.expense.merchant} por ${money(proposal.expense.amount)}."
            is ChatProposal.Edit -> "Listo, actualicé ${proposal.after.merchant}."
            is ChatProposal.Delete -> "Listo, borré ${proposal.expense.merchant} (${money(proposal.expense.amount)})."
        }
        ConfirmResult.STALE -> "Ese gasto cambió mientras tanto, así que no toqué nada. Pedímelo de nuevo."
        ConfirmResult.FAILED -> "No pude guardarlo. Probá de nuevo."
    }

    private fun summary(r: ChatReply.Summary): String {
        val scope = scope(r.from, r.to, r.categoryId)
        return when (r.metric) {
            ChatMetric.COUNT -> if (r.count == 0) "No encontré gastos$scope." else "Tuviste ${r.count} ${if (r.count == 1) "gasto" else "gastos"}$scope."
            ChatMetric.AVERAGE -> if (r.count == 0) "No encontré gastos$scope." else "Tu gasto promedio$scope es ${money(r.total)} (sobre ${r.count})."
            else -> if (r.count == 0) "No encontré gastos$scope." else "Gastaste ${money(r.total)}$scope (${r.count} ${if (r.count == 1) "gasto" else "gastos"})."
        }
    }

    private fun scope(from: LocalDate?, to: LocalDate?, categoryId: String?): String {
        val parts = mutableListOf<String>()
        if (categoryId != null) parts += "en ${categoryLabel(categoryId)}"
        when {
            from != null && to != null && from == to -> parts += "el ${day.format(from)}"
            from != null && to != null -> parts += "del ${day.format(from)} al ${day.format(to)}"
            from != null -> parts += "desde el ${day.format(from)}"
            to != null -> parts += "hasta el ${day.format(to)}"
        }
        return if (parts.isEmpty()) "" else " " + parts.joinToString(" ")
    }

    private fun listing(r: ChatReply.Listing): String {
        if (r.totalCount == 0) return "No encontré gastos."
        val lines = r.items.joinToString("\n") { "• ${dateTime.format(Instant.ofEpochMilli(it.occurredAt))}  ${it.merchant} — ${money(it.amount)}" }
        val more = if (r.totalCount > r.items.size) "\n…y ${r.totalCount - r.items.size} más." else ""
        return "${r.totalCount} ${if (r.totalCount == 1) "gasto" else "gastos"}, ${money(r.total)} en total:\n$lines$more"
    }

    private fun describe(e: Expense) =
        "${e.merchant} — ${money(e.amount)}, ${categoryLabel(e.categoryId)}, ${dateTime.format(Instant.ofEpochMilli(e.occurredAt))}."

    private fun proposal(p: ChatProposal): String = when (p) {
        is ChatProposal.Create -> "¿Son correctos los datos?\n" + fields(p.expense) + "\nSi algo está mal, decime qué cambiar."
        is ChatProposal.Delete -> "¿Borro este gasto?\n" + fields(p.expense) + "\nNo se puede deshacer."
        is ChatProposal.Edit -> buildString {
            append("¿Hago este cambio?\n• Comercio: ${p.before.merchant}")
            if (p.after.merchant != p.before.merchant) append(" → ${p.after.merchant}")
            append("\n• Monto: ${money(p.before.amount)}")
            if (p.after.amount != p.before.amount) append(" → ${money(p.after.amount)}")
            append("\n• Categoría: ${categoryLabel(p.before.categoryId)}")
            if (p.after.categoryId != p.before.categoryId) append(" → ${categoryLabel(p.after.categoryId)}")
            append("\nSi algo está mal, decime qué cambiar.")
        }
    }

    private fun fields(e: Expense) =
        "• Comercio: ${e.merchant}\n• Monto: ${money(e.amount)}\n• Categoría: ${categoryLabel(e.categoryId)}\n• Fecha: ${dateTime.format(Instant.ofEpochMilli(e.occurredAt))}"

    private fun clarify(c: ChatReply.Clarify): String = when (c.reason) {
        ClarifyReason.NOT_FOUND -> if (c.detail != null) "No encontré ningún gasto de \"${c.detail}\"." else "No encontré ese gasto."
        ClarifyReason.AMBIGUOUS_REFERENCE -> "Hay varios gastos de \"${c.detail}\". Decime cuál (con el monto o la fecha) o pedime la lista."
        ClarifyReason.INVALID_AMOUNT -> "No entendí el monto. Decímelo en guaraníes, por ejemplo 45000."
        ClarifyReason.INVALID_DATE -> "Esa fecha no me sirve. Usá una fecha pasada o de hoy."
        ClarifyReason.UNKNOWN_CATEGORY -> "No tengo la categoría \"${c.detail}\". Decime una de tus categorías."
        ClarifyReason.DATE_NOT_EDITABLE -> "Todavía no puedo cambiar la fecha de un gasto. Podés borrarlo y cargarlo de nuevo."
        ClarifyReason.INVALID_RANGE -> "El rango de fechas está al revés. ¿Desde cuándo y hasta cuándo?"
        ClarifyReason.UNREADABLE -> "No entendí eso. ¿Podés decirlo de otra forma?"
        ClarifyReason.EMPTY_PHRASE -> "Escribime algo."
        ClarifyReason.NOTHING_TO_REVISE -> "No tengo ninguna propuesta pendiente para corregir. Decime qué querés cargar, editar o borrar."
    }
}
