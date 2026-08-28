package com.eliasgonzalez.expensetracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `solo separadores y simbolos sin ningun digito da vacio`() {
        assertEquals("", sanitizeAmountInput("₲.,-  ."))
        assertEquals("", sanitizeAmountInput("   "))
        assertEquals("", sanitizeAmountInput("$-.,%"))
    }

    @Test
    fun `exactamente 14 digitos no se corta`() {
        val input = "1".repeat(14)
        val result = sanitizeAmountInput(input)
        assertEquals(14, result.length)
        assertEquals(input, result)
    }

    @Test
    fun `exactamente 15 digitos no se corta`() {
        val input = "9".repeat(15)
        val result = sanitizeAmountInput(input)
        assertEquals(15, result.length)
        assertEquals(input, result)
    }

    @Test
    fun `exactamente 16 digitos se corta a 15`() {
        val input = "1".repeat(16)
        val result = sanitizeAmountInput(input)
        assertEquals(15, result.length)
        assertEquals("1".repeat(15), result)
    }

    @Test
    fun `sanitizar es idempotente`() {
        val casos = listOf(
            "₲50.000",
            "1a2b3c",
            "1".repeat(30),
            "",
            "   ",
            "999999999999999",
        )
        for (caso in casos) {
            val once = sanitizeAmountInput(caso)
            val twice = sanitizeAmountInput(once)
            assertEquals("sanitize no es idempotente para '$caso'", once, twice)
        }
    }

    @Test
    fun `digitos unicode no ASCII pasan el filtro isDigit pero el string resultante no es 0-9 puro`() {
        // Char.isDigit() en Kotlin delega en Character.isDigit(), que acepta
        // dígitos Unicode de otros sistemas numéricos (arábigo-índico,
        // ancho-completo, etc.), no solo '0'..'9'. sanitizeAmountInput los deja
        // pasar creyendo que son "dígitos válidos", así que el resultado no es
        // garantizado que sea un string compuesto solo por '0'..'9' ASCII -
        // algo que el nombre/comentario de la función ("solo dígitos") sugiere
        // pero no impone. Nota: esto NO rompe toLongOrNull() (ver test
        // siguiente) porque Kotlin también resuelve dígitos Unicode ahí, pero
        // sí puede sorprender a cualquier otro código que asuma ASCII puro
        // (ej. comparaciones de String, longitud en bytes, regex "[0-9]+").
        val arabigoIndico = "٠١٢٣٤" // dígitos arábigo-índico 0-4
        val sanitizedArabigo = sanitizeAmountInput(arabigoIndico)
        assertTrue(
            "se esperaba que sanitizeAmountInput aceptara los dígitos arábigo-índico " +
                "como 'dígitos válidos', pero los filtró: '$sanitizedArabigo'",
            sanitizedArabigo.isNotEmpty(),
        )
        assertFalse(
            "el resultado contiene dígitos no-ASCII, no es un string '0'..'9' puro",
            sanitizedArabigo.all { it in '0'..'9' },
        )

        val anchoCompleto = "１２３４５" // dígitos de ancho completo (fullwidth) 1-5
        val sanitizedAnchoCompleto = sanitizeAmountInput(anchoCompleto)
        assertTrue(
            "se esperaba que sanitizeAmountInput aceptara los dígitos de ancho completo " +
                "como 'dígitos válidos', pero los filtró: '$sanitizedAnchoCompleto'",
            sanitizedAnchoCompleto.isNotEmpty(),
        )
        assertFalse(
            "el resultado contiene dígitos no-ASCII, no es un string '0'..'9' puro",
            sanitizedAnchoCompleto.all { it in '0'..'9' },
        )
    }

    @Test
    fun `digitos unicode no ASCII si son parseables por toLongOrNull - Kotlin resuelve Character digit, no hay bug de parseo`() {
        // Verificado empíricamente: Kotlin's String.toLongOrNull() usa
        // Character.digit(char, radix) internamente, el mismo mecanismo que
        // hace que Char.isDigit() acepte estos caracteres. Por eso el pipeline
        // completo (sanitize -> toLongOrNull) es consistente para dígitos
        // Unicode: "٠١٢٣٤" (arábigo-índico 0,1,2,3,4) parsea a 1234 (el cero a
        // la izquierda se pierde igual que con ASCII "01234".toLongOrNull()).
        assertEquals(1234L, sanitizeAmountInput("٠١٢٣٤").toLongOrNull())
        assertEquals(12345L, sanitizeAmountInput("１２３４５").toLongOrNull())
    }

    @Test
    fun `digitos unicode mezclados con ASCII se concatenan y parsean como Long normal`() {
        // Mezcla de dígito arábigo-índico con dígitos ASCII: el filtro deja
        // pasar ambos tipos y toLongOrNull() los interpreta consistentemente
        // como sus valores numéricos, dando 123 (no null).
        val mezcla = "1٢3" // '1', dígito arábigo-índico '2', '3'
        val sanitized = sanitizeAmountInput(mezcla)
        assertEquals(3, sanitized.length)
        assertEquals(123L, sanitized.toLongOrNull())
    }
}
