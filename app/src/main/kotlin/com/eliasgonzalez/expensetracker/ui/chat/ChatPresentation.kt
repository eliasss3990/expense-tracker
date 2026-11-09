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
        is ChatReply.Talk -> tidy(reply.text).ifBlank { "Decime qué querés consultar o cargar." }
        ChatReply.Cancelled -> "Listo, lo descarté. No se guardó nada."
        ChatReply.Help -> HELP_TEXT
    }

    fun confirmed(result: ConfirmResult, proposal: ChatProposal): String = when (result) {
        ConfirmResult.DONE -> when (proposal) {
            is ChatProposal.Create -> "Listo, cargué ${proposal.expense.description.ifBlank { proposal.expense.merchant }} por ${money(proposal.expense.amount)}."
            is ChatProposal.Edit -> "Listo, actualicé ${proposal.after.merchant}."
            is ChatProposal.Delete -> "Listo, borré ${proposal.expense.merchant} (${money(proposal.expense.amount)})."
            is ChatProposal.CategoryCreate -> "Listo, creé la categoría ${proposal.label}."
            is ChatProposal.CategoryRename -> "Listo, la categoría ahora se llama ${proposal.newLabel}."
            is ChatProposal.CategoryDelete -> "Listo, borré la categoría ${proposal.entry.label}."
            is ChatProposal.CategoryDeactivate -> "Listo, desactivé la categoría ${proposal.entry.label}."
        }
        ConfirmResult.STALE -> "Eso cambió mientras tanto, así que no toqué nada. Pedímelo de nuevo."
        ConfirmResult.FAILED -> "No pude guardarlo. Probá de nuevo."
    }

    /**
     * La charla libre la genera el modelo con un tope de largo: si se corto a mitad de frase se recorta hasta la ultima
     * oracion completa (o la ultima palabra) en vez de mostrar "…SUBSCRIPTIONS, T,".
     */
    fun tidy(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty() || text.last() in ".!?…") return text
        val end = text.indexOfLast { it in ".!?" }
        if (end >= MIN_SENTENCE) return text.substring(0, end + 1)
        val space = text.lastIndexOf(' ')
        return (if (space > MIN_SENTENCE) text.substring(0, space) else text).trimEnd(',', ';', ':', ' ') + "…"
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
        if (r.totalCount == 0) return "No encontré gastos${scope(r.from, r.to, r.categoryId)}."
        val lines = r.items.joinToString("\n") { "• ${dateTime.format(Instant.ofEpochMilli(it.occurredAt))}  ${it.merchant} — ${money(it.amount)}" }
        val more = if (r.totalCount > r.items.size) "\n…y ${r.totalCount - r.items.size} más." else ""
        return "${r.totalCount} ${if (r.totalCount == 1) "gasto" else "gastos"}${scope(r.from, r.to, r.categoryId)}, ${money(r.total)} en total:\n$lines$more"
    }

    private fun describe(e: Expense) =
        (if (e.description.isBlank()) "" else "${e.description} · ") + "${e.merchant} — ${money(e.amount)}, ${categoryLabel(e.categoryId)}, ${dateTime.format(Instant.ofEpochMilli(e.occurredAt))}."

    private fun proposal(p: ChatProposal): String = when (p) {
        is ChatProposal.Create -> "¿Son correctos los datos?\n" + fields(p.expense) + "\nSi algo está mal, decime qué cambiar."
        is ChatProposal.Delete -> "¿Borro este gasto?\n" + fields(p.expense) + "\nNo se puede deshacer."
        is ChatProposal.CategoryCreate -> "¿Creo esta categoría?\n• Nombre: ${p.label}\nSi algo está mal, decime qué cambiar."
        is ChatProposal.CategoryRename ->
            "¿Renombro la categoría?\n• Nombre: ${p.before.label} → ${p.newLabel}\nLos gastos que ya la usan se mantienen.\nSi algo está mal, decime qué cambiar."
        is ChatProposal.CategoryDelete -> "¿Borro la categoría ${p.entry.label}?\nNo tiene gastos. No se puede deshacer."
        is ChatProposal.CategoryDeactivate ->
            "La categoría ${p.entry.label} ya tiene gastos, así que no se puede borrar. ¿La desactivo?\nDeja de aparecer para elegir, pero tus gastos anteriores la conservan."
        is ChatProposal.Edit -> buildString {
            append("¿Hago este cambio?\n")
            if (p.before.description.isNotBlank() || p.after.description != p.before.description) {
                append("• Descripción: ${p.before.description.ifBlank { "—" }}")
                if (p.after.description != p.before.description) append(" → ${p.after.description.ifBlank { "—" }}")
                append("\n")
            }
            append("• Comercio: ${p.before.merchant}")
            if (p.after.merchant != p.before.merchant) append(" → ${p.after.merchant}")
            append("\n• Monto: ${money(p.before.amount)}")
            if (p.after.amount != p.before.amount) append(" → ${money(p.after.amount)}")
            append("\n• Categoría: ${categoryLabel(p.before.categoryId)}")
            if (p.after.categoryId != p.before.categoryId) append(" → ${categoryLabel(p.after.categoryId)}")
            append("\nSi algo está mal, decime qué cambiar.")
        }
    }

    private fun fields(e: Expense) =
        (if (e.description.isBlank()) "" else "• Descripción: ${e.description}\n") + "• Comercio: ${e.merchant}\n• Monto: ${money(e.amount)}\n• Categoría: ${categoryLabel(e.categoryId)}\n• Fecha: ${dateTime.format(Instant.ofEpochMilli(e.occurredAt))}"

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
        ClarifyReason.PENDING_IS_DELETE -> "La propuesta pendiente es borrar ese gasto. Confirmala o cancelala; si querés otra cosa, decímelo después."
        ClarifyReason.CATEGORY_NOT_FOUND -> if (c.detail != null) "No encontré ninguna categoría llamada \"${c.detail}\"." else "No encontré esa categoría."
        ClarifyReason.AMBIGUOUS_CATEGORY -> "Hay varias categorías que coinciden con \"${c.detail}\". Decime el nombre completo."
        ClarifyReason.DUPLICATE_CATEGORY -> "Ya existe una categoría llamada \"${c.detail}\" (o es el mismo nombre que tiene ahora)."
        ClarifyReason.INVALID_CATEGORY_NAME -> "Ese nombre no sirve: tiene que tener texto y hasta 40 caracteres."
        ClarifyReason.PROTECTED_CATEGORY -> "La categoría \"${c.detail}\" es la de respaldo y no se puede borrar ni desactivar."
        ClarifyReason.ALREADY_INACTIVE -> "La categoría \"${c.detail}\" tiene gastos y ya está desactivada."
        ClarifyReason.WRONG_PENDING_KIND -> "Esa corrección no aplica a la propuesta pendiente. Confirmala, cancelala o decime otra cosa."
        ClarifyReason.NOTHING_TO_REVISE -> "No tengo ninguna propuesta pendiente para corregir. Decime qué querés cargar, editar o borrar."
    }

    private companion object {
        const val MIN_SENTENCE = 20
        val HELP_TEXT = """
            Puedo ayudarte con tus gastos:
            • Consultar: cuánto gastaste hoy, ayer, esta semana, este mes o entre dos fechas, por categoría; contarlos, promediarlos o listarlos.
            • Contarte de un gasto: el último, el más caro o el de un comercio.
            • Cargar un gasto nuevo, editar el monto, el comercio o la categoría de uno, y borrarlo. Siempre te lo muestro antes y lo confirmás vos.

            • Categorías: crear una, renombrarla o borrarla (si ya tiene gastos, la desactivo en vez de borrarla). También lo confirmás vos.

            Lo que no puedo hacer: cambiar el color de una categoría, cambiar la fecha de un gasto ya cargado, ni tocar varios gastos o categorías de una vez.
        """.trimIndent()
    }
}
