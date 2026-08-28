package com.eliasgonzalez.expensetracker.notification

/**
 * Parser dedicado para Banco Familiar / Eko (canal "Familiar A Toda
 * Hora", remitente de mail `operbancaweb`, vía Gmail).
 *
 * Una transferencia saliente ("TRANSFERENCIA A OTRAS ENTIDADES") genera
 * DOS mails de confirmación distintos para la MISMA operación, con
 * ~11s de diferencia observados en la práctica: uno "CARGADA CON
 * EXITO" y otro "CONFIRMADA" (mismo Nro. de Operación en ambos).
 * `GenericPurchaseParser` los detectaba como dos gastos separados
 * porque su extracción de comercio genérica ("en X") no daba el mismo
 * resultado en los dos textos. Acá se extrae siempre el mismo campo
 * ("Cliente Beneficiario") y el mismo monto ("Monto a enviar") de
 * ambas variantes, así el mecanismo de deduplicación ya existente
 * (`CreateCandidate`, ventana de 5 min, mismo monto+comercio) los
 * colapsa en un solo candidato sin necesidad de lógica extra acá.
 *
 * Una transferencia ENTRANTE usa el texto "TRANSFERENCIA DE OTRAS
 * ENTIDADES RECIBIDA" (nótese "DE", no "A") - deliberadamente NO
 * matcheada acá, para que caiga al chequeo genérico de palabras clave
 * de ingreso (`GenericPurchaseParser.incomeKeywordRegex`), que ya la
 * excluye correctamente.
 */
object BancoFamiliarParser : NotificationParser {
    private const val GMAIL_PACKAGE = "com.google.android.gm"

    private val senderRegex = Regex("""operbancaweb""", RegexOption.IGNORE_CASE)
    private val outgoingRegex = Regex("""TRANSFERENCIA\s+A\s+OTRAS\s+ENTIDADES""", RegexOption.IGNORE_CASE)
    private val amountRegex = Regex(
        """Monto\s+a\s+enviar:?\s*\n?\s*(?:Gs\.?|₲|PYG)\s*([\d.,]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val beneficiaryRegex = Regex(
        """Cliente\s+Beneficiario:?\s*\n?\s*([A-ZÀ-Ÿ0-9 '&.,-]{2,60}?)\s*\n""",
        RegexOption.IGNORE_CASE,
    )

    override fun canHandle(context: NotificationContext): Boolean {
        if (context.packageName != GMAIL_PACKAGE) return false
        if (!senderRegex.containsMatchIn(context.title)) return false
        val body = "${context.title} ${context.text} ${context.bigText}"
        return outgoingRegex.containsMatchIn(body) && amountRegex.containsMatchIn(body)
    }

    override fun parse(context: NotificationContext): ParseResult? {
        val body = "${context.title} ${context.text} ${context.bigText}"
        val amount = amountRegex.find(body)
            ?.groupValues?.get(1)
            ?.replace(".", "")
            ?.replace(",", "")
            ?.toLongOrNull() ?: return null

        val beneficiary = beneficiaryRegex.find(body)?.groupValues?.get(1)?.trim()
        return ParseResult(
            amount = amount,
            merchant = beneficiary ?: "Transferencia Banco Familiar",
            merchantConfident = beneficiary != null,
            currency = "PYG",
            confidence = if (beneficiary != null) 0.95 else 0.7,
            parserId = "banco-familiar-email-v1",
        )
    }
}
