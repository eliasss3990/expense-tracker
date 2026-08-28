package com.eliasgonzalez.expensetracker.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParserEngineTest {

    private fun bankAppNotification(title: String, text: String) = NotificationContext(
        packageName = "py.com.elcomercio.retailbanking",
        applicationName = "ueno bank",
        title = title,
        text = text,
        bigText = "",
        timestamp = 0L,
    )

    private fun genericNotification(text: String, applicationName: String = "OtroBanco") = NotificationContext(
        packageName = "com.otrobanco.app",
        applicationName = applicationName,
        title = "",
        text = text,
        bigText = "",
        timestamp = 0L,
    )

    @Test
    fun `parser especifico tiene prioridad sobre el generico`() {
        val context = bankAppNotification("Enviaste Gs. 800.000", "detalle")

        val result = ParserEngine.parse(context)!!

        assertEquals("ueno-bank-app-v1", result.parserId)
        assertEquals(800_000L, result.amount)
    }

    @Test
    fun `si ningun parser especifico puede manejarlo cae al generico`() {
        val context = genericNotification("Compraste Gs. 25.000 en Farmacia")

        val result = ParserEngine.parse(context)!!

        assertEquals("generic-purchase-v1", result.parserId)
        assertEquals(25_000L, result.amount)
        assertEquals("Farmacia", result.merchant)
    }

    @Test
    fun `si ningun parser puede manejarlo devuelve null`() {
        val context = genericNotification("Hola, este es un mensaje sin monto")

        assertNull(ParserEngine.parse(context))
    }

    @Test
    fun `notificacion vacia no matchea ningun parser`() {
        val context = genericNotification("")

        assertNull(ParserEngine.parse(context))
    }

    @Test
    fun `parser especifico que puede manejar pero decide ignorar - no cae al generico`() {
        // UenoBankParser.canHandle es true para un "Recibiste" (porque hay monto), pero
        // parse() devuelve null a proposito porque es un ingreso, no un gasto. El motor
        // NO debe intentar el parser generico en ese caso: una vez que un parser especifico
        // "reclama" la notificacion via canHandle, su decision es final.
        val context = bankAppNotification("Recibiste Gs. 800.000", "Conocé el detalle de la transferencia.")

        assertNull(ParserEngine.parse(context))
    }

    @Test
    fun `parser especifico con canHandle true pero monto que desborda Long - no cae al generico y devuelve null`() {
        val context = bankAppNotification("Enviaste Gs. 999999999999999999999999", "detalle")

        assertNull(ParserEngine.parse(context))
    }

    @Test
    fun `mail de ueno bank usa el parser especifico de email`() {
        val context = NotificationContext(
            packageName = "com.google.android.gm",
            applicationName = "Gmail",
            title = "ueno bank",
            text = "Transferencia realizada",
            bigText = "Monto Gs. 800.000 Beneficiario ROBERTO GONZALEZ Entidad beneficiario Banco Itau",
            timestamp = 0L,
        )

        val result = ParserEngine.parse(context)!!
        assertEquals("ueno-bank-email-v1", result.parserId)
    }

    @Test
    fun `mail de otro remitente con monto cae al parser generico`() {
        val context = NotificationContext(
            packageName = "com.google.android.gm",
            applicationName = "Gmail",
            title = "Netflix",
            text = "Tu factura de Gs. 85.000 ya está disponible en Netflix",
            bigText = "",
            timestamp = 0L,
        )

        val result = ParserEngine.parse(context)!!
        assertEquals("generic-purchase-v1", result.parserId)
        assertEquals(85_000L, result.amount)
    }

    @Test
    fun `notificacion solo con espacios no matchea ningun parser`() {
        val context = genericNotification("   \t\n  ")

        assertNull(ParserEngine.parse(context))
    }
}
