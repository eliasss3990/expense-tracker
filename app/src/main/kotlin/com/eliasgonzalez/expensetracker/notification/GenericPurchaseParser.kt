package com.eliasgonzalez.expensetracker.notification

/**
 * Parser genérico: no está atado a un banco específico, sirve de red de
 * contención para fuentes sin parser propio (cuando una fuente sí tiene
 * uno, ese se prueba primero - ver ParserEngine).
 *
 * Restringido a un paquete conocido en [TRUSTED_PACKAGES] - antes
 * aceptaba una notificación de CUALQUIER app instalada con tal de que el
 * texto matcheara el patrón de monto ("Gs./₲/PYG" + número). Como el
 * `packageName` lo fija el sistema desde el UID que posta la
 * notificación (no se puede falsificar desde otra app), cualquier app
 * maliciosa/comprometida en el mismo celular podía publicar una
 * notificación con texto tipo "Compraste Gs. 5.000.000 en Comercio
 * Falso S.A." y aparecer como un gasto detectado legítimo con
 * Aceptar/Editar/Rechazar. Requerir un permiso explícito no alcanzaba
 * (el acceso a notificaciones ya es todo-o-nada), así que la única
 * defensa real es no confiar en el contenido de una app que no está en
 * la lista.
 *
 * Para agregar un banco/billetera nuevo: confirmar el paquete real
 * (ej. via `adb shell dumpsys package <nombre> | grep packageName`, o
 * revisando la notificación con un inspector) y sumarlo acá - nunca a
 * ciegas, un paquete mal escrito simplemente no matchea nunca (falla
 * cerrado, no abre un agujero).
 */
object GenericPurchaseParser : NotificationParser {

    private val TRUSTED_PACKAGES = setOf(
        "py.com.elcomercio.retailbanking", // Ueno Bank (misma fuente que UenoBankParser)
        "com.google.android.gm", // Gmail (mails de confirmacion de compra/transferencia)
        "com.google.android.apps.walletnfcrel", // Google Wallet
    )

    private val amountRegex = Regex("""(?:Gs\.?|₲|PYG)\s*([\d.,]+)""", RegexOption.IGNORE_CASE)
    private val merchantRegex = Regex("""(?:en|in)\s+([A-Za-zÀ-ÿ0-9 '&.-]{2,40})""", RegexOption.IGNORE_CASE)

    override fun canHandle(context: NotificationContext): Boolean {
        if (context.packageName !in TRUSTED_PACKAGES) return false
        val body = "${context.title} ${context.text} ${context.bigText}"
        return amountRegex.containsMatchIn(body)
    }

    override fun parse(context: NotificationContext): ParseResult? {
        if (context.packageName !in TRUSTED_PACKAGES) return null
        val body = "${context.title} ${context.text} ${context.bigText}"
        val amountMatch = amountRegex.find(body) ?: return null
        val amount = amountMatch.groupValues[1]
            .replace(".", "")
            .replace(",", "")
            .toLongOrNull() ?: return null

        val matchedMerchant = merchantRegex.find(body)
            ?.groupValues?.get(1)
            ?.trim()
            ?.trimEnd('.', ',')
            ?.takeIf { it.isNotBlank() }

        return ParseResult(
            amount = amount,
            merchant = matchedMerchant ?: context.applicationName,
            merchantConfident = matchedMerchant != null,
            currency = "PYG",
            confidence = 0.6,
            parserId = "generic-purchase-v1",
        )
    }
}

data class ParseResult(
    val amount: Long,
    val merchant: String,
    val merchantConfident: Boolean = true,
    val currency: String,
    val confidence: Double,
    val parserId: String,
)
