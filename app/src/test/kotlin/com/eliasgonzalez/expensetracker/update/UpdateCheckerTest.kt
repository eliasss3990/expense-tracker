package com.eliasgonzalez.expensetracker.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `version remota mayor en el ultimo segmento es mas nueva`() {
        assertTrue(isNewerVersion("0.3.0", "0.2.0"))
    }

    @Test
    fun `version remota menor no es mas nueva`() {
        assertFalse(isNewerVersion("0.1.0", "0.2.0"))
    }

    @Test
    fun `version identica no es mas nueva`() {
        assertFalse(isNewerVersion("0.2.0", "0.2.0"))
    }

    @Test
    fun `compara numericamente, no alfabeticamente - 0-10-0 es mayor que 0-9-0`() {
        assertTrue(isNewerVersion("0.10.0", "0.9.0"))
        assertFalse(isNewerVersion("0.9.0", "0.10.0"))
    }

    @Test
    fun `mayor version en un segmento mas significativo gana aunque el siguiente sea menor`() {
        assertTrue(isNewerVersion("1.0.0", "0.99.99"))
        assertFalse(isNewerVersion("0.99.99", "1.0.0"))
    }

    @Test
    fun `segmentos faltantes cuentan como cero`() {
        assertTrue(isNewerVersion("0.3", "0.2.9"))
        assertFalse(isNewerVersion("0.2", "0.2.1"))
        assertTrue(isNewerVersion("0.2.1", "0.2"))
    }

    @Test
    fun `segmentos no numericos cuentan como cero sin crashear`() {
        assertFalse(isNewerVersion("poc", "0.0.0"))
        assertFalse(isNewerVersion("", ""))
    }
}
