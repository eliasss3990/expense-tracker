package com.eliasgonzalez.expensetracker.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UenoBankParserTest {

    private fun bankAppNotification(title: String, text: String) = NotificationContext(
        packageName = "py.com.elcomercio.retailbanking",
        applicationName = "ueno bank",
        title = title,
        text = text,
        bigText = "",
        timestamp = 0L,
    )

    private fun gmailNotification(bigText: String) = NotificationContext(
        packageName = "com.google.android.gm",
        applicationName = "Gmail",
        title = "ueno bank",
        text = "Transferencia realizada",
        bigText = bigText,
        timestamp = 0L,
    )

    @Test
    fun `notificacion push sin beneficiario - merchant de relleno, no confiable`() {
        val context = bankAppNotification("Enviaste Gs. 800.000", "Conocé el detalle de la transferencia.")

        assertTrue(UenoBankParser.canHandle(context))
        val result = UenoBankParser.parse(context)!!

        assertEquals(800_000L, result.amount)
        assertFalse(result.merchantConfident)
        assertEquals("ueno-bank-app-v1", result.parserId)
    }

    @Test
    fun `mail extrae el beneficiario real, no el titular de la cuenta debito`() {
        val bigText = "Enviamos tu transferencia. Podés descargar el detalle de la operación desde " +
            "los movimientos de tu app. Monto Gs. 800.000 Beneficiario ROBERTO GONZALEZ QUIONEZ " +
            "Entidad beneficiario BANCO ITAU PARAGUAY S.A. Titular cuenta débito ELIAS MARTIN GONZALEZ MEZA"
        val context = gmailNotification(bigText)

        assertTrue(UenoBankParser.canHandle(context))
        val result = UenoBankParser.parse(context)!!

        assertEquals(800_000L, result.amount)
        assertEquals("ROBERTO GONZALEZ QUIONEZ", result.merchant)
        assertTrue(result.merchantConfident)
        assertEquals("ueno-bank-email-v1", result.parserId)
    }

    @Test
    fun `dinero recibido se ignora - no es un gasto`() {
        val context = bankAppNotification("Recibiste Gs. 800.000", "Conocé el detalle de la transferencia.")

        assertNull(UenoBankParser.parse(context))
    }

    @Test
    fun `gmail de otro remitente no lo maneja`() {
        val context = NotificationContext(
            packageName = "com.google.android.gm",
            applicationName = "Gmail",
            title = "Netflix",
            text = "Tu factura de Gs. 85.000 ya está disponible",
            bigText = "",
            timestamp = 0L,
        )

        assertFalse(UenoBankParser.canHandle(context))
    }
}
