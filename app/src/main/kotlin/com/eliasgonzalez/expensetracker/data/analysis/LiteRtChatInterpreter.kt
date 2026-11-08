package com.eliasgonzalez.expensetracker.data.analysis

import com.eliasgonzalez.expensetracker.domain.chat.ChatIntent
import com.eliasgonzalez.expensetracker.domain.chat.ChatInterpreter
import com.eliasgonzalez.expensetracker.domain.chat.ChatRequest

/** Tope de tokens de salida del chat: una accion es un JSON corto (la respuesta de charla es lo mas largo). */
private const val CHAT_MAX_OUTPUT_TOKENS = 200

/**
 * Adaptador de [ChatInterpreter] sobre el motor compartido [LiteRtGenerator]. Sin estado propio: el contexto del chat
 * llega en cada [ChatRequest] (la sesion lo guarda en memoria), asi cada pedido es independiente y reproducible.
 * Los fallos del motor (modelo ausente, timeout) se propagan; una salida que no se entiende es [ChatIntent.Unreadable].
 */
class LiteRtChatInterpreter(private val generator: LiteRtGenerator) : ChatInterpreter {

    override suspend fun interpret(request: ChatRequest): ChatIntent {
        val raw = generator.generate(
            GenerationRequest(
                system = ChatPrompts.system(request.today, request.categories),
                user = ChatPrompts.user(request),
                schema = ChatSchema.build(request.categories),
                schemaKey = "chat",
                maxOutputTokens = CHAT_MAX_OUTPUT_TOKENS,
            ),
        )
        return ChatOutputParser.parse(raw)
    }
}
