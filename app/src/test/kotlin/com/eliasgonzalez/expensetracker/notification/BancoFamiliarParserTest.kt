package com.eliasgonzalez.expensetracker.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BancoFamiliarParserTest {

    private fun mailNotification(text: String, bigText: String) = NotificationContext(
        packageName = "com.google.android.gm",
        applicationName = "Gmail",
        title = "operbancaweb",
        text = text,
        bigText = bigText,
        timestamp = 0L,
    )

    // Formato real de un mail de "transferencia cargada" (datos
    // anonimizados - banco/nombres/numeros de operacion reemplazados,
    // pero la estructura de etiquetas y saltos de linea es la real).
    private fun cargadaBigText(monto: String = "PYG 250.000") = """
        TRANSFERENCIA A OTRAS ENTIDADES CARGADA CON EXITO
        Hemos registrado la siguiente operación de transferencia a otras entidades realizada a través de Familiar A Toda Hora:
        Nro. de Operación:
        000000000
        Fecha y hora de Operación:
        28/08/2026 16:06:20
        Entidad Pagadora:
        BANCO EJEMPLO
        Cliente Pagador:
        JUAN PEREZ
        Nro. de Cuenta del Pagador:
        ******0000
        Monto a enviar:
        $monto
        Entidad Beneficiaria:
        BANCO EJEMPLO DOS S.A.
        Cliente Beneficiario:
        MARIA LOPEZ
        Nro. de Cuenta del Beneficiario:
        0000000000000
    """.trimIndent()

    // Segundo mail para la MISMA operación, ~11s después en la práctica,
    // con encabezado y algunos campos distintos (numero de referencia
    // agregado, entidad pagadora con razon social completa, prefijo de
    // moneda distinto) pero el mismo monto y el mismo beneficiario.
    private fun confirmadaBigText(monto: String = "Gs. 250.000") = """
        TRANSFERENCIA A OTRAS ENTIDADES CONFIRMADA
        Hemos registrado la siguiente operación de transferencia a otras entidades realizada a través de Familiar A Toda Hora:
        Nro. de Operación:
        000000000
        Referencia:
        REFEJEMPLO000000000000000
        Fecha y hora de Operación:
        28/08/2026 16:06:31
        Entidad Pagadora:
        BANCO EJEMPLO S.A.E.C.A.
        Cliente Pagador:
        JUAN PEREZ
        Nro. de Cuenta del Pagador:
        **************0000
        Monto a enviar:
        $monto
        Entidad Beneficiaria:
        BANCO EJEMPLO DOS S.A.
        Cliente Beneficiario:
        MARIA LOPEZ
        Nro. de Cuenta del Beneficiario:
        0000000000000
        Razón de comunicación:
        La transferencia ha sido acreditada al Participante Beneficiario.
    """.trimIndent()

    @Test
    fun `mail de transferencia cargada se detecta como gasto con el beneficiario real`() {
        val context = mailNotification("Transferencia a otros bancos", cargadaBigText())

        assertTrue(BancoFamiliarParser.canHandle(context))
        val result = BancoFamiliarParser.parse(context)!!

        assertEquals(250_000L, result.amount)
        assertEquals("MARIA LOPEZ", result.merchant)
        assertTrue(result.merchantConfident)
        assertEquals("banco-familiar-email-v1", result.parserId)
    }

    @Test
    fun `mail de transferencia confirmada (segundo aviso de la misma operacion) se detecta igual`() {
        val context = mailNotification("Transferencia a otros bancos", confirmadaBigText())

        assertTrue(BancoFamiliarParser.canHandle(context))
        val result = BancoFamiliarParser.parse(context)!!

        assertEquals(250_000L, result.amount)
        assertEquals("MARIA LOPEZ", result.merchant)
    }

    // ---------- hallazgo real (2026-08-28): dos mails para la misma
    // operacion deben producir el MISMO monto+comercio, para que la
    // deduplicacion de CreateCandidate los colapse en un solo gasto en
    // vez de duplicarlo. Antes (con GenericPurchaseParser) el comercio
    // extraido difería entre los dos mails y no deduplicaba. ----------

    @Test
    fun `los dos mails de la misma operacion producen el mismo monto y comercio - requisito para la deduplicacion`() {
        val cargada = BancoFamiliarParser.parse(mailNotification("x", cargadaBigText()))!!
        val confirmada = BancoFamiliarParser.parse(mailNotification("x", confirmadaBigText()))!!

        assertEquals(cargada.amount, confirmada.amount)
        assertEquals(cargada.merchant, confirmada.merchant)
    }

    @Test
    fun `prefijo de moneda PYG y Gs dan el mismo monto`() {
        val conPyg = BancoFamiliarParser.parse(mailNotification("x", cargadaBigText(monto = "PYG 1.500.000")))!!
        val conGs = BancoFamiliarParser.parse(mailNotification("x", cargadaBigText(monto = "Gs. 1.500.000")))!!

        assertEquals(1_500_000L, conPyg.amount)
        assertEquals(1_500_000L, conGs.amount)
    }

    // ---------- transferencias entrantes: no las maneja este parser ----------

    @Test
    fun `transferencia entrante (RECIBIDA, con DE no A) no la maneja este parser`() {
        val bigText = """
            TRANSFERENCIA DE OTRAS ENTIDADES RECIBIDA
            Hemos registrado la siguiente operación de transferencia
            Entidad Pagadora:
            BANCO EJEMPLO
            Cliente Pagador:
            JUAN PEREZ
            Moneda y Monto:
            PYG 250.000
            Entidad Beneficiaria:
            BANCO EJEMPLO DOS S.A.
            Cliente Beneficiario:
            Juan Perez
        """.trimIndent()
        val context = mailNotification("Transferencia de otros bancos", bigText)

        assertFalse(BancoFamiliarParser.canHandle(context))
        assertNull(BancoFamiliarParser.parse(context))
    }

    @Test
    fun `remitente distinto de operbancaweb no lo maneja este parser`() {
        val context = NotificationContext(
            packageName = "com.google.android.gm",
            applicationName = "Gmail",
            title = "otro-remitente",
            text = "Transferencia a otros bancos",
            bigText = cargadaBigText(),
            timestamp = 0L,
        )

        assertFalse(BancoFamiliarParser.canHandle(context))
    }

    @Test
    fun `paquete distinto de Gmail no lo maneja este parser`() {
        val context = NotificationContext(
            packageName = "py.com.elcomercio.retailbanking",
            applicationName = "operbancaweb",
            title = "operbancaweb",
            text = "Transferencia a otros bancos",
            bigText = cargadaBigText(),
            timestamp = 0L,
        )

        assertFalse(BancoFamiliarParser.canHandle(context))
    }

    @Test
    fun `sin beneficiario detectable usa un merchant de relleno no confiable`() {
        val bigText = cargadaBigText().replace("Cliente Beneficiario:\nMARIA LOPEZ\n", "")
        val context = mailNotification("Transferencia a otros bancos", bigText)

        val result = BancoFamiliarParser.parse(context)!!
        assertFalse(result.merchantConfident)
        assertEquals("Transferencia Banco Familiar", result.merchant)
    }
}
