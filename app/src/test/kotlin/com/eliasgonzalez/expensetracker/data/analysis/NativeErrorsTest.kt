package com.eliasgonzalez.expensetracker.data.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class NativeErrorsTest {
    @Test
    fun `una cancelacion del motor que la app no pidio es un fallo y no una cancelacion de la corrutina`() {
        val mapped = NativeErrors.map(CancellationException("kCancelled"), cancelRequestedByApp = false)
        assertFalse("no debe poder confundirse con la cancelacion del llamador", mapped is CancellationException)
        assertTrue(mapped is IllegalStateException)
    }

    @Test
    fun `una cancelacion que la app pidio se respeta tal cual`() {
        val original = CancellationException("kCancelled")
        assertSame(original, NativeErrors.map(original, cancelRequestedByApp = true))
    }

    @Test
    fun `cualquier otro error pasa sin tocar`() {
        val boom = RuntimeException("x")
        assertSame(boom, NativeErrors.map(boom, cancelRequestedByApp = false))
        assertSame(boom, NativeErrors.map(boom, cancelRequestedByApp = true))
    }
}
