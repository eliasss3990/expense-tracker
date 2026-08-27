package com.eliasgonzalez.expensetracker.notification

/**
 * Parser genérico de POC: no está atado a un banco específico.
 * Busca un patrón de monto (Gs./₲/PYG + número) en cualquier notificación
 * y, si lo encuentra, intenta extraer el comercio de un "en <comercio>".
 *
 * Una etapa futura reemplaza esto por parsers por fuente (Itaú, Ueno,
 * Google Wallet, etc.) con canHandle()/parse() propios.
 */
object GenericPurchaseParser {

    private val amountRegex = Regex("""(?:Gs\.?|₲|PYG)\s*([\d.,]+)""", RegexOption.IGNORE_CASE)
    private val merchantRegex = Regex("""(?:en|in)\s+([A-Za-zÀ-ÿ0-9 '&.-]{2,40})""", RegexOption.IGNORE_CASE)

    fun canHandle(context: NotificationContext): Boolean {
        val body = "${context.title} ${context.text} ${context.bigText}"
        return amountRegex.containsMatchIn(body)
    }

    fun parse(context: NotificationContext): ParseResult? {
        val body = "${context.title} ${context.text} ${context.bigText}"
        val amountMatch = amountRegex.find(body) ?: return null
        val amount = amountMatch.groupValues[1]
            .replace(".", "")
            .replace(",", "")
            .toLongOrNull() ?: return null

        val merchant = merchantRegex.find(body)
            ?.groupValues?.get(1)
            ?.trim()
            ?.trimEnd('.', ',')
            ?.takeIf { it.isNotBlank() }
            ?: context.applicationName

        return ParseResult(
            amount = amount,
            merchant = merchant,
            currency = "PYG",
            confidence = 0.6,
            parserId = "generic-purchase-poc-v1",
        )
    }
}

data class ParseResult(
    val amount: Long,
    val merchant: String,
    val currency: String,
    val confidence: Double,
    val parserId: String,
)
