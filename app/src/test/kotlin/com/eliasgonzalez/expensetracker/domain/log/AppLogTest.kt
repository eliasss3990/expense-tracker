package com.eliasgonzalez.expensetracker.domain.log

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AppLog es global (como Timber): estos tests instalan un destino que registra
 * las llamadas y lo restauran a LogSink.None al terminar para no filtrar estado
 * a otros tests.
 */
class AppLogTest {

    private data class Entry(val level: LogLevel, val tag: String, val message: String, val throwable: Throwable?)

    private class RecordingSink : LogSink {
        val entries = mutableListOf<Entry>()
        override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
            entries += Entry(level, tag, message, throwable)
        }
    }

    @After
    fun restoreDefaultSink() = AppLog.install(LogSink.None)

    @Test
    fun `cada nivel llega al destino con su etiqueta y mensaje`() {
        val sink = RecordingSink().also { AppLog.install(it) }

        AppLog.d("A", "debug")
        AppLog.i("B", "info")
        AppLog.w("C", "warn")
        AppLog.e("D", "error")

        assertEquals(
            listOf(
                Entry(LogLevel.DEBUG, "A", "debug", null),
                Entry(LogLevel.INFO, "B", "info", null),
                Entry(LogLevel.WARN, "C", "warn", null),
                Entry(LogLevel.ERROR, "D", "error", null),
            ),
            sink.entries,
        )
    }

    @Test
    fun `el error asociado llega intacto al destino`() {
        val sink = RecordingSink().also { AppLog.install(it) }
        val boom = IllegalStateException("boom")

        AppLog.e("Tag", "fallo", boom)
        AppLog.w("Tag", "aviso", boom)

        assertSame(boom, sink.entries[0].throwable)
        assertSame(boom, sink.entries[1].throwable)
    }

    @Test
    fun `sin error asociado el throwable llega null`() {
        val sink = RecordingSink().also { AppLog.install(it) }

        AppLog.w("Tag", "aviso")

        assertNull(sink.entries.single().throwable)
    }

    @Test
    fun `sin destino instalado no hace nada ni lanza`() {
        AppLog.install(LogSink.None)

        AppLog.e("Tag", "fallo", RuntimeException("x"))
        AppLog.d("Tag", "debug")
        // llegar aca sin excepcion es el resultado esperado
    }

    @Test
    fun `un destino que lanza NO rompe a quien esta logueando`() {
        AppLog.install(object : LogSink {
            override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
                throw IllegalStateException("el destino se rompio")
            }
        })

        AppLog.e("Tag", "esto no debe propagar la excepcion del destino")
        AppLog.w("Tag", "ni esto", RuntimeException("x"))
    }

    @Test
    fun `instalar otro destino reemplaza al anterior - cambiar el destino es un solo punto`() {
        val primero = RecordingSink().also { AppLog.install(it) }
        AppLog.i("T", "uno")

        val segundo = RecordingSink().also { AppLog.install(it) }
        AppLog.i("T", "dos")

        assertEquals(listOf("uno"), primero.entries.map { it.message })
        assertEquals(listOf("dos"), segundo.entries.map { it.message })
    }

    @Test
    fun `los niveles son los cuatro esperados y en orden de severidad`() {
        assertEquals(
            listOf(LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR),
            LogLevel.entries,
        )
        assertTrue(LogLevel.ERROR.ordinal > LogLevel.WARN.ordinal)
    }
}
