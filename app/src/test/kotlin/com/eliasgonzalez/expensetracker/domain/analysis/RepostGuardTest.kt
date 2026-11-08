package com.eliasgonzalez.expensetracker.domain.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepostGuardTest {

    private val body = listOf("com.google.android.gm", "Banco", "Compra Gs. 85.000", "")

    @Test
    fun `la primera vez no es repeticion y la misma notificacion re-publicada si`() {
        val guard = RepostGuard()
        assertFalse(guard.isRepeat("0|gm|1", body))
        assertTrue(guard.isRepeat("0|gm|1", body))
        assertTrue(guard.isRepeat("0|gm|1", body))
    }

    @Test
    fun `si el contenido cambia con la misma clave es un aviso nuevo`() {
        val guard = RepostGuard()
        assertFalse(guard.isRepeat("k", body))
        assertFalse(guard.isRepeat("k", listOf("com.google.android.gm", "Banco", "Compra Gs. 90.000", "")))
        assertTrue(guard.isRepeat("k", listOf("com.google.android.gm", "Banco", "Compra Gs. 90.000", "")))
    }

    @Test
    fun `mismo contenido con otra clave es otra notificacion`() {
        val guard = RepostGuard()
        assertFalse(guard.isRepeat("a", body))
        assertFalse(guard.isRepeat("b", body))
    }

    @Test
    fun `la memoria es acotada y olvida lo mas viejo`() {
        val guard = RepostGuard(capacity = 2)
        guard.isRepeat("a", body); guard.isRepeat("b", body); guard.isRepeat("c", body)
        assertFalse("a ya fue olvidada", guard.isRepeat("a", body))
    }
}
