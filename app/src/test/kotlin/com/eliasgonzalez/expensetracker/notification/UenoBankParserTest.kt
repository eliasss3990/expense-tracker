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

    // ---------- vacios / espacios ----------

    @Test
    fun `notificacion vacia del banco no matchea`() {
        val context = bankAppNotification("", "")

        assertFalse(UenoBankParser.canHandle(context))
        assertNull(UenoBankParser.parse(context))
    }

    @Test
    fun `notificacion solo con espacios no matchea`() {
        val context = bankAppNotification("   ", "\t\n ")

        assertFalse(UenoBankParser.canHandle(context))
    }

    @Test
    fun `bank app sin monto no matchea aunque diga Enviaste`() {
        val context = bankAppNotification("Enviaste dinero", "a un contacto")

        assertFalse(UenoBankParser.canHandle(context))
    }

    // ---------- monto ambiguo / overflow / negativos ----------

    @Test
    fun `monto que desborda Long - canHandle true pero parse null`() {
        val context = bankAppNotification("Enviaste Gs. 999999999999999999999999", "detalle")

        assertTrue(UenoBankParser.canHandle(context))
        assertNull(UenoBankParser.parse(context))
    }

    @Test
    fun `coma decimal se trata como separador de miles - ambiguedad documentada`() {
        val context = bankAppNotification("Enviaste Gs. 10,50", "detalle")

        assertEquals(1050L, UenoBankParser.parse(context)!!.amount)
    }

    @Test
    fun `numero negativo pegado al prefijo Gs produce un match vacio - bug documentado`() {
        // Mismo comportamiento que en GenericPurchaseParser: "Gs\.?" backtrackea dejando
        // el punto literal disponible para el propio grupo [\d.,]+, que termina
        // capturando solo "." (vacio tras remover separadores). canHandle() da true
        // pero parse() da null porque toLongOrNull("") es null.
        val context = bankAppNotification("Enviaste Gs. -800.000", "detalle")

        assertTrue(UenoBankParser.canHandle(context))
        assertNull(UenoBankParser.parse(context))
    }

    @Test
    fun `multiples montos en la notificacion push toma el primero`() {
        val context = bankAppNotification("Enviaste Gs. 800.000", "de un total de Gs. 2.000.000")

        assertEquals(800_000L, UenoBankParser.parse(context)!!.amount)
    }

    @Test
    fun `monto en cero se parsea como cero`() {
        val context = bankAppNotification("Enviaste Gs. 0", "detalle")

        assertEquals(0L, UenoBankParser.parse(context)!!.amount)
    }

    // ---------- bank app: canHandle true pero parse null ----------

    @Test
    fun `bank app con monto pero sin Enviaste ni Recibiste - canHandle true, parse null`() {
        val context = bankAppNotification("Tu saldo actual es Gs. 800.000", "consulta de saldo")

        assertTrue(UenoBankParser.canHandle(context))
        assertNull(UenoBankParser.parse(context))
    }

    @Test
    fun `bank app con Recibiste y Enviaste a la vez se ignora por prevalecer Recibiste`() {
        val context = bankAppNotification("Enviaste Gs. 800.000, Recibiste Gs. 100.000", "detalle")

        assertNull(UenoBankParser.parse(context))
    }

    // ---------- gmail: beneficiario ----------

    @Test
    fun `beneficiario en minusculas tambien se detecta por ignore case`() {
        val bigText = "Monto Gs. 800.000 Beneficiario roberto gonzalez Entidad beneficiario Banco Itau"
        val context = gmailNotification(bigText)

        val result = UenoBankParser.parse(context)!!
        assertEquals("roberto gonzalez", result.merchant)
        assertTrue(result.merchantConfident)
    }

    @Test
    fun `beneficiario con guion apostrofe y ampersand se preserva`() {
        val bigText = "Monto Gs. 800.000 Beneficiario JOSE O'BRIEN-GONZALEZ & CIA Entidad beneficiario Banco Itau"
        val context = gmailNotification(bigText)

        val result = UenoBankParser.parse(context)!!
        assertEquals("JOSE O'BRIEN-GONZALEZ & CIA", result.merchant)
    }

    @Test
    fun `beneficiario sin marcador de cierre - ni salto de linea ni Entidad - cae al fallback`() {
        val bigText = "Monto Gs. 800.000 Beneficiario ROBERTO GONZALEZ"
        val context = gmailNotification(bigText)

        val result = UenoBankParser.parse(context)!!
        assertEquals("Transferencia Ueno Bank", result.merchant)
        assertFalse(result.merchantConfident)
    }

    @Test
    fun `beneficiario con salto de linea como cierre`() {
        val bigText = "Monto Gs. 800.000 Beneficiario ROBERTO GONZALEZ\nEntidad beneficiario Banco Itau"
        val context = gmailNotification(bigText)

        val result = UenoBankParser.parse(context)!!
        assertEquals("ROBERTO GONZALEZ", result.merchant)
    }

    @Test
    fun `dos beneficiarios en el mismo mail toma el primero`() {
        val bigText = "Monto Gs. 800.000 Beneficiario PRIMERO PEREZ Entidad beneficiario Banco Itau " +
            "Beneficiario SEGUNDO LOPEZ Entidad beneficiario Banco Otro"
        val context = gmailNotification(bigText)

        val result = UenoBankParser.parse(context)!!
        assertEquals("PRIMERO PEREZ", result.merchant)
    }

    @Test
    fun `mail con Recibiste se ignora igual que en la app`() {
        val bigText = "Recibiste una transferencia. Monto Gs. 800.000 Beneficiario ROBERTO GONZALEZ Entidad beneficiario Banco"
        val context = gmailNotification(bigText)

        assertNull(UenoBankParser.parse(context))
    }

    @Test
    fun `mail con monto que desborda Long - canHandle true pero parse null`() {
        val bigText = "Monto Gs. 999999999999999999999999 Beneficiario ROBERTO GONZALEZ Entidad beneficiario Banco"
        val context = gmailNotification(bigText)

        assertTrue(UenoBankParser.canHandle(context))
        assertNull(UenoBankParser.parse(context))
    }

    @Test
    fun `gmail sin monto no matchea aunque el titulo sea ueno bank`() {
        val context = gmailNotification("Hola, gracias por confiar en Ueno Bank")

        assertFalse(UenoBankParser.canHandle(context))
    }

    @Test
    fun `gmail con Ueno Bank en el body pero no en el titulo no matchea`() {
        val context = NotificationContext(
            packageName = "com.google.android.gm",
            applicationName = "Gmail",
            title = "Confirmacion de transferencia",
            text = "Gracias por usar ueno bank. Monto Gs. 800.000",
            bigText = "",
            timestamp = 0L,
        )

        assertFalse(UenoBankParser.canHandle(context))
    }

    @Test
    fun `paquete desconocido nunca matchea aunque el texto sea identico al de la app`() {
        val context = NotificationContext(
            packageName = "com.otra.app",
            applicationName = "Otra app",
            title = "Enviaste Gs. 800.000",
            text = "detalle",
            bigText = "",
            timestamp = 0L,
        )

        assertFalse(UenoBankParser.canHandle(context))
        assertNull(UenoBankParser.parse(context))
    }
}
