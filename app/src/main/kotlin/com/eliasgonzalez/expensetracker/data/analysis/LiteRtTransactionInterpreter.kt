package com.eliasgonzalez.expensetracker.data.analysis

import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationRequest
import com.eliasgonzalez.expensetracker.domain.analysis.InterpretationResult
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionInterpreter

/** Tope de tokens de salida: la respuesta es un JSON chico; mas que esto es un modelo desbocado. */
private const val MAX_OUTPUT_TOKENS = 400

/**
 * Adaptador de [TransactionInterpreter] (avisos de gasto) sobre el motor compartido [LiteRtGenerator]: arma el prompt
 * y el esquema restringido con las categorias del usuario y valida la salida con el parser tolerante.
 */
class LiteRtTransactionInterpreter(
    private val generator: LiteRtGenerator,
    private val modelId: String,
) : TransactionInterpreter {

    override suspend fun interpret(request: InterpretationRequest): InterpretationResult {
        val started = System.nanoTime()
        val raw = generator.generate(
            GenerationRequest(
                system = InterpretationPrompts.system(request.pass),
                user = InterpretationPrompts.user(request),
                schema = InterpretationSchema.build(request.categories),
                schemaKey = "transaction",
                maxOutputTokens = MAX_OUTPUT_TOKENS,
            ),
        )
        val interpretation = ModelOutputParser.parse(raw)
        return InterpretationResult(interpretation, request.pass, modelId, (System.nanoTime() - started) / 1_000_000)
    }
}
