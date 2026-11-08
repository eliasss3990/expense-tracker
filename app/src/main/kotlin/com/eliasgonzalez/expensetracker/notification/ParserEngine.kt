package com.eliasgonzalez.expensetracker.notification

import com.eliasgonzalez.expensetracker.domain.analysis.ParserEvidence
import com.eliasgonzalez.expensetracker.domain.analysis.ParserSnapshot

/**
 * Prueba los parsers dedicados por fuente antes de caer al genérico.
 * Devuelve null si ningún parser puede manejar la notificación, o si el
 * que la maneja decide explícitamente ignorarla (ej. UenoBankParser con
 * un ingreso de dinero).
 */
object ParserEngine {
    private val specificParsers = listOf(UenoBankParser, BancoFamiliarParser)

    fun parse(context: NotificationContext): ParseResult? {
        val specific = specificParsers.firstOrNull { it.canHandle(context) }
        if (specific != null) return specific.parse(context)

        if (!GenericPurchaseParser.canHandle(context)) return null
        return GenericPurchaseParser.parse(context)
    }

    /**
     * Lo mismo que [parse] pero sin perder la diferencia entre "no la conocia ningun parser" y "un
     * parser la descarto a proposito": la segunda es una regla curada que el analisis con IA respeta.
     */
    fun evidence(context: NotificationContext): ParserEvidence {
        val specific = specificParsers.firstOrNull { it.canHandle(context) }
        if (specific != null && specific.explicitlyIgnores(context)) {
            return ParserEvidence.ExplicitlyIgnored(specific::class.simpleName.orEmpty())
        }
        val result = parse(context) ?: return ParserEvidence.NoParser
        return ParserEvidence.Parsed(
            ParserSnapshot(
                amount = result.amount,
                currency = result.currency,
                merchant = result.merchant,
                merchantConfident = result.merchantConfident,
                parserId = result.parserId,
                confidence = result.confidence,
                // Solo un parser dedicado a un formato conocido es curado; el generico no.
                curated = specific != null,
            )
        )
    }
}
