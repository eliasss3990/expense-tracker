package com.eliasgonzalez.expensetracker.notification

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
     * Los parsers dedicados conservan prioridad. Para paquetes verificados de la
     * allowlist, la IA también puede analizar formatos nuevos sin que el parser
     * genérico reconozca el monto, siempre que haya indicios financieros.
     */
    fun isEligibleForAnalysis(context: NotificationContext): Boolean =
        specificParsers.any { it.canHandle(context) } ||
            (GenericPurchaseParser.isTrustedPackage(context.packageName) &&
                (GenericPurchaseParser.canHandle(context) || GenericPurchaseParser.hasFinancialHint(context)))
}
