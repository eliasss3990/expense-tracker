package com.eliasgonzalez.expensetracker.domain.chat

import com.eliasgonzalez.expensetracker.domain.analysis.CategoryOption
import java.time.LocalDate

/**
 * UN chat abierto: recuerda lo hablado y la propuesta pendiente para que el modelo entienda "no, era 50000".
 * Vive solo en memoria y se descarta al cerrar el chat: no hay lista de chats ni nada persistido entre ellos.
 * El contexto esta acotado ([maxTurns]) para no inflar el prompt del modelo. No es seguro entre hilos: lo usa
 * una sola pantalla a la vez.
 */
class ChatSession(private val assistant: ChatAssistant, private val maxTurns: Int = MAX_TURNS) {
    private val turns = ArrayDeque<ChatTurn>()

    /** La propuesta a la vista, aun sin confirmar ni cancelar. */
    var pending: ChatProposal? = null
        private set

    val history: List<ChatTurn> get() = turns.toList()

    suspend fun send(phrase: String, today: LocalDate, categories: List<CategoryOption>): ChatReply {
        val reply = assistant.ask(ChatRequest(phrase, today, categories, history = history, pending = pending))
        if (phrase.isNotBlank()) {
            remember(ChatTurn(TurnRole.USER, phrase.take(MAX_TURN_CHARS)))
            remember(ChatTurn(TurnRole.ASSISTANT, summarize(reply)))
        }
        when (reply) {
            is ChatReply.Confirm -> pending = reply.proposal
            ChatReply.Cancelled -> pending = null
            else -> Unit // una consulta o una pregunta no descarta la propuesta que el usuario tiene a la vista
        }
        return reply
    }

    /** Ejecuta la propuesta a la vista. Solo se llama desde la accion explicita del usuario (el boton). */
    suspend fun confirm(): ConfirmResult? {
        val proposal = pending ?: return null
        val result = assistant.confirm(proposal)
        if (result != ConfirmResult.FAILED) pending = null
        remember(ChatTurn(TurnRole.ASSISTANT, "Confirmado: ${proposal.summary()} ($result)"))
        return result
    }

    fun cancel() {
        if (pending != null) remember(ChatTurn(TurnRole.ASSISTANT, "Cancelado: ${pending!!.summary()}"))
        pending = null
    }

    /** Resumen CORTO y sin datos de mas de lo que el usuario ya vio: es lo que ve el modelo como contexto. */
    private fun summarize(reply: ChatReply): String = when (reply) {
        is ChatReply.Summary -> "Respondi ${reply.metric} = ${reply.total} (${reply.count} gastos)"
        is ChatReply.Listing -> "Liste ${reply.items.size} de ${reply.totalCount} gastos"
        is ChatReply.Detail -> "Conte del gasto ${reply.expense.merchant} ${reply.expense.amount}"
        is ChatReply.Confirm -> "Propuse: ${reply.proposal.summary()}. Espero confirmacion o correccion."
        is ChatReply.Clarify -> "No pude: ${reply.reason}"
        is ChatReply.Talk -> reply.text.take(MAX_TURN_CHARS)
        ChatReply.Cancelled -> "Propuesta cancelada"
        ChatReply.Help -> "Expliqué qué puedo hacer"
    }

    private fun remember(turn: ChatTurn) {
        turns.addLast(turn)
        while (turns.size > maxTurns) turns.removeFirst()
    }

    private companion object {
        const val MAX_TURNS = 8
        const val MAX_TURN_CHARS = 200
    }
}
