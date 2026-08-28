package com.eliasgonzalez.expensetracker.notification

/**
 * Parser dedicado para Ueno Bank (paquete real: py.com.elcomercio.retailbanking).
 * Dos fuentes muy distintas para el mismo movimiento:
 *
 * - Notificación push de la app: "Enviaste Gs. 800.000 / Conocé el
 *   detalle de la transferencia." - NO trae el beneficiario, solo monto.
 * - Mail de confirmación (vía Gmail): trae el detalle completo (Monto,
 *   Beneficiario, Entidad beneficiario, Titular cuenta débito). El
 *   beneficiario real está en el campo "Beneficiario" - el campo "Titular
 *   cuenta débito" es el dueño de la cuenta (uno mismo), no el comercio.
 *
 * "Recibiste" (dinero entrante) se ignora a propósito: no es un gasto.
 */
object UenoBankParser : NotificationParser {
    private const val BANK_APP_PACKAGE = "py.com.elcomercio.retailbanking"
    private const val GMAIL_PACKAGE = "com.google.android.gm"

    private val amountRegex = Regex("""Gs\.?\s*([\d.,]+)""", RegexOption.IGNORE_CASE)
    private val sentRegex = Regex("""Enviaste""", RegexOption.IGNORE_CASE)
    private val receivedRegex = Regex("""Recibiste""", RegexOption.IGNORE_CASE)
    private val emailSenderRegex = Regex("""ueno\s*bank""", RegexOption.IGNORE_CASE)
    private val beneficiaryRegex = Regex(
        """Beneficiario\s*\n?\s*([A-ZÀ-Ÿ0-9 '&.-]{2,60}?)\s*(?:\n|Entidad)""",
        RegexOption.IGNORE_CASE,
    )

    override fun canHandle(context: NotificationContext): Boolean {
        val body = "${context.title} ${context.text} ${context.bigText}"
        return when (context.packageName) {
            BANK_APP_PACKAGE -> amountRegex.containsMatchIn(body)
            GMAIL_PACKAGE -> emailSenderRegex.containsMatchIn(context.title) && amountRegex.containsMatchIn(body)
            else -> false
        }
    }

    override fun parse(context: NotificationContext): ParseResult? {
        val body = "${context.title} ${context.text} ${context.bigText}"
        if (receivedRegex.containsMatchIn(body)) return null // ingreso, no es un gasto

        val amount = amountRegex.find(body)
            ?.groupValues?.get(1)
            ?.replace(".", "")
            ?.replace(",", "")
            ?.toLongOrNull() ?: return null

        return when (context.packageName) {
            BANK_APP_PACKAGE -> {
                if (!sentRegex.containsMatchIn(body)) return null
                ParseResult(
                    amount = amount,
                    merchant = "Transferencia Ueno Bank",
                    merchantConfident = false,
                    currency = "PYG",
                    confidence = 0.9,
                    parserId = "ueno-bank-app-v1",
                )
            }
            GMAIL_PACKAGE -> {
                val beneficiary = beneficiaryRegex.find(body)?.groupValues?.get(1)?.trim()
                ParseResult(
                    amount = amount,
                    merchant = beneficiary ?: "Transferencia Ueno Bank",
                    merchantConfident = beneficiary != null,
                    currency = "PYG",
                    confidence = if (beneficiary != null) 0.95 else 0.7,
                    parserId = "ueno-bank-email-v1",
                )
            }
            else -> null
        }
    }
}
