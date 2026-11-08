package com.eliasgonzalez.expensetracker.notification

/**
 * Filtro barato ANTES del modelo: una notificacion de una fuente confiable que ni menciona dinero
 * (un newsletter en Gmail, un "tu sesion vence") no justifica despertar un modelo de varios GB.
 * No decide nada sobre el movimiento (eso lo hace el modelo): solo descarta lo que no habla de plata.
 * Es deliberadamente generoso; ante la duda, pasa.
 */
object FinancialPrefilter {
    private val amount = Regex(
        // En un string crudo de Kotlin "$" seguido de letra INTERPOLA: se escribe como ${'$'}.
        """(?:Gs\.?|₲|PYG|USD|U${'$'}S|US${'$'}|EUR|€|${'$'})\s*\d|\d\s*(?:Gs\b|₲|PYG|USD|EUR)""",
        RegexOption.IGNORE_CASE,
    )

    private val hints = listOf(
        "compra", "pago", "pagaste", "transferencia", "transaccion", "transacción", "debito", "débito",
        "credito", "crédito", "tarjeta", "retiro", "cobro", "consumo", "reembolso", "devolucion", "devolución",
        "purchase", "payment", "paid", "transfer", "debit", "credit", "card", "withdraw", "refund",
    )

    fun mentionsMoney(context: NotificationContext): Boolean {
        val body = "${context.title} ${context.text} ${context.bigText}"
        if (amount.containsMatchIn(body)) return true
        val lower = body.lowercase()
        return hints.any { it in lower }
    }
}
