package com.eliasgonzalez.expensetracker.notification

import com.eliasgonzalez.expensetracker.domain.analysis.SourceTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedSourcesTest {

    private fun ctx(pkg: String, title: String = "operbancaweb", body: String) =
        NotificationContext(pkg, "App", title, body, "", 0L)

    // Un cuerpo que cada parser dedicado reconoceria si el paquete fuera el suyo.
    private val uenoBody = "Enviaste Gs. 100.000 a alguien"
    private val familiarBody = "TRANSFERENCIA A OTRAS ENTIDADES\nMonto a enviar: Gs. 100.000\nCliente Beneficiario: JUAN\n"

    @Test
    fun `un parser dedicado solo reconoce paquetes de la lista de fuentes confiables`() {
        // Si alguien suma un parser (o un paquete a un parser) sin sumarlo a TrustedSources, el evento
        // llegaria como Parsed pero con confianza UNTRUSTED y nunca se auto-registraria, con un motivo
        // engañoso en la auditoria. Este test lo vuelve un error visible.
        val unlisted = "com.paquete.no.listado"
        for (parser in listOf(UenoBankParser, BancoFamiliarParser)) {
            assertFalse(parser::class.simpleName, parser.canHandle(ctx(unlisted, body = uenoBody)))
            assertFalse(parser::class.simpleName, parser.canHandle(ctx(unlisted, body = familiarBody)))
        }
        for (pkg in listOf("py.com.elcomercio.retailbanking", "com.google.android.gm")) {
            assertTrue(pkg, pkg in TrustedSources.packages)
        }
    }

    @Test
    fun `cada fuente tiene su nivel de confianza y las desconocidas ninguno`() {
        assertEquals(SourceTrust.BANK, TrustedSources.trustOf("py.com.elcomercio.retailbanking"))
        assertEquals(SourceTrust.BANK, TrustedSources.trustOf("com.google.android.apps.walletnfcrel"))
        assertEquals(SourceTrust.MAILBOX, TrustedSources.trustOf("com.google.android.gm"))
        assertNull(TrustedSources.trustOf("com.otra.app"))
    }
}
