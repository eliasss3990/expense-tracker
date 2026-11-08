package com.eliasgonzalez.expensetracker.notification

import com.eliasgonzalez.expensetracker.domain.analysis.SourceTrust

/**
 * Fuentes cuyas notificaciones se consideran financieras. El `packageName` lo fija el sistema desde
 * el UID que publica (no se puede falsificar desde otra app), pero el CONTENIDO solo es confiable si
 * lo genera la entidad: en una app de banco si, en un buzon (Gmail) lo escribe cualquiera que te mande un
 * mail. Por eso cada fuente lleva su nivel de confianza (ver SourceTrust).
 *
 * Para sumar un banco/billetera: confirmar el paquete real (`adb shell dumpsys package <nombre>`);
 * un paquete mal escrito simplemente no matchea nunca (falla cerrado).
 */
object TrustedSources {
    private val trust = mapOf(
        "py.com.elcomercio.retailbanking" to SourceTrust.BANK, // Ueno Bank
        "com.google.android.apps.walletnfcrel" to SourceTrust.BANK, // Google Wallet
        "com.google.android.gm" to SourceTrust.MAILBOX, // Gmail
    )

    val packages: Set<String> = trust.keys

    fun trustOf(packageName: String): SourceTrust? = trust[packageName]
}
