package com.eliasgonzalez.expensetracker.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserEngineEligibilityTest {

    private fun context(
        packageName: String,
        title: String = "",
        text: String = "",
        bigText: String = "",
    ) = NotificationContext(
        packageName = packageName,
        applicationName = "Test app",
        title = title,
        text = text,
        bigText = bigText,
        timestamp = 1_700_000_000_000,
    )

    @Test
    fun `gmail con formato financiero nuevo entra a IA aunque el parser no extraiga monto`() {
        val notification = context(
            packageName = "com.google.android.gm",
            title = "Payment completed",
            text = "Your transaction was approved for USD 45.00",
        )

        assertTrue(ParserEngine.isEligibleForAnalysis(notification))
        assertTrue(ParserEngine.parse(notification) == null)
    }

    @Test
    fun `transferencia entrante sigue siendo elegible para que IA la clasifique`() {
        val notification = context(
            packageName = "com.google.android.gm",
            title = "Transferencia recibida",
            text = "Recibiste una transferencia de Gs. 250.000",
        )

        assertTrue(ParserEngine.isEligibleForAnalysis(notification))
        assertTrue(ParserEngine.parse(notification) == null)
    }

    @Test
    fun `paquete no confiable no entra aunque el texto parezca un gasto`() {
        val notification = context(
            packageName = "com.example.malicious",
            title = "Compra aprobada",
            text = "Pagaste Gs. 5.000.000 en Comercio Falso",
        )

        assertFalse(ParserEngine.isEligibleForAnalysis(notification))
    }

    @Test
    fun `notificacion no financiera de gmail no se procesa`() {
        val notification = context(
            packageName = "com.google.android.gm",
            title = "Reunión mañana",
            text = "Te esperamos a las 10:00",
        )

        assertFalse(ParserEngine.isEligibleForAnalysis(notification))
    }
}
