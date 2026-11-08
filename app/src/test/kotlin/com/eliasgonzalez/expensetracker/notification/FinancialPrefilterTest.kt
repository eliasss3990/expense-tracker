package com.eliasgonzalez.expensetracker.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialPrefilterTest {

    private fun ctx(text: String, title: String = "Correo") = NotificationContext("com.google.android.gm", "Gmail", title, text, "", 0L)

    @Test
    fun `una palabra de dinero sin cifra pasa para un banco pero no para un buzon`() {
        val mail = ctx("Tu pago de la tarjeta fue procesado, gracias por tu compra")
        assertTrue(FinancialPrefilter.mentionsMoney(mail))
        assertFalse(FinancialPrefilter.mentionsMoney(mail, requireFigure = true))
    }

    @Test
    fun `con una cifra de dinero pasa tambien en un buzon`() {
        assertTrue(FinancialPrefilter.mentionsMoney(ctx("Compra aprobada por Gs. 85.000"), requireFigure = true))
        assertTrue(FinancialPrefilter.mentionsMoney(ctx("You paid USD 25.00 at Amazon"), requireFigure = true))
    }

    @Test
    fun `un correo sin dinero no pasa en ningun caso`() {
        assertFalse(FinancialPrefilter.mentionsMoney(ctx("Reunion mañana a las 10"), requireFigure = true))
        assertFalse(FinancialPrefilter.mentionsMoney(ctx("Reunion mañana a las 10")))
    }
}
