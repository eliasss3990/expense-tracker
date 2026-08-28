package com.eliasgonzalez.expensetracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInputTest {

    @Test
    fun `descarta todo lo que no sea digito`() {
        assertEquals("50000", sanitizeAmountInput("₲50.000"))
        assertEquals("123", sanitizeAmountInput("1a2b3c"))
    }

    @Test
    fun `corta a 15 digitos como maximo`() {
        val input = "1".repeat(30)
        val result = sanitizeAmountInput(input)
        assertEquals(15, result.length)
        assertEquals("1".repeat(15), result)
    }

    @Test
    fun `no toca un monto ya dentro del limite`() {
        assertEquals("999999999999999", sanitizeAmountInput("999999999999999"))
    }

    @Test
    fun `cadena vacia se mantiene vacia`() {
        assertEquals("", sanitizeAmountInput(""))
    }
}
