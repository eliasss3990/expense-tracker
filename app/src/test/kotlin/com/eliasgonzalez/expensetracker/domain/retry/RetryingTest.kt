package com.eliasgonzalez.expensetracker.domain.retry

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RetryingTest {

    @Test
    fun `si funciona al primer intento no reintenta ni espera`() = runTest {
        var calls = 0

        val ok = retrying(attempts = 3, delayMillis = 500) { calls++ }

        assertTrue(ok)
        assertEquals(1, calls)
        assertEquals(0, currentTime)
    }

    @Test
    fun `se recupera de un fallo transitorio y espera con backoff lineal`() = runTest {
        var calls = 0

        val ok = retrying(attempts = 3, delayMillis = 500) {
            calls++
            if (calls < 3) throw IllegalStateException("base bloqueada")
        }

        assertTrue(ok)
        assertEquals(3, calls)
        assertEquals("500 ms tras el 1er fallo + 1000 ms tras el 2do", 1_500, currentTime)
    }

    @Test
    fun `si fallan todos los intentos devuelve false y avisa cada fallo`() = runTest {
        val failures = mutableListOf<Pair<Int, String?>>()

        val ok = retrying(
            attempts = 3,
            delayMillis = 100,
            onFailure = { attempt, e -> failures += attempt to e.message },
        ) { throw IllegalStateException("roto") }

        assertFalse(ok)
        assertEquals(listOf(1 to "roto", 2 to "roto", 3 to "roto"), failures)
    }

    @Test
    fun `no espera despues del ultimo intento fallido`() = runTest {
        retrying(attempts = 2, delayMillis = 1_000) { throw IllegalStateException("x") }

        assertEquals("solo la espera entre el 1ro y el 2do", 1_000, currentTime)
    }

    @Test
    fun `la cancelacion se propaga y no se reintenta`() = runTest {
        var calls = 0

        try {
            retrying(attempts = 3, delayMillis = 100) {
                calls++
                throw CancellationException("cancelado")
            }
            fail("debia propagar la cancelacion")
        } catch (e: CancellationException) {
            assertEquals(1, calls)
        }
    }

    @Test
    fun `no se puede pedir cero intentos`() = runTest {
        try {
            retrying(attempts = 0, delayMillis = 0) { }
            fail("debia rechazar attempts = 0")
        } catch (e: IllegalArgumentException) {
            // esperado
        }
    }
}
