package com.eliasgonzalez.expensetracker.data.analysis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ResourceHolderTest {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()

    @After
    fun tearDown() = executor.shutdownNow().let { }

    private class Engine

    @Test
    fun `si se cancela mientras el recurso se crea, queda guardado y se puede cerrar (no se filtra)`() = runBlocking {
        // Repro del review: initialize() bloquea y no se interrumpe; la cancelacion llega en medio.
        val creating = CountDownLatch(1)
        val finish = CountDownLatch(1)
        var closed = 0
        var created = 0
        val holder = ResourceHolder(dispatcher, { created++; creating.countDown(); finish.await(5, TimeUnit.SECONDS); Engine() }, { closed++ })

        // En otro hilo: un launch dentro de runBlocking no arranca mientras se bloquea el hilo principal con el latch.
        val caller = launch(Dispatchers.Default) { holder.get() }
        assertTrue("la creacion debia haber empezado", creating.await(5, TimeUnit.SECONDS))
        caller.cancel()
        finish.countDown()
        caller.join()

        assertNotNull("el recurso ya creado no puede perderse", holder.current())
        val again = holder.get()
        assertEquals("el siguiente pedido reutiliza, no crea otro", 1, created)
        assertSame(holder.current(), again)
        holder.release()
        assertEquals("y se cierra", 1, closed)
        assertNull(holder.current())
    }

    @Test
    fun `se crea una sola vez aunque se pida muchas`() = runBlocking {
        var created = 0
        val holder = ResourceHolder(dispatcher, { created++; Engine() }, {})
        repeat(5) { holder.get() }
        assertEquals(1, created)
    }

    @Test
    fun `release cierra, olvida y deja volver a crear`() = runBlocking {
        var created = 0
        var closed = 0
        val holder = ResourceHolder(dispatcher, { created++; Engine() }, { closed++ })
        holder.get(); holder.release(); holder.get()
        assertEquals(2, created); assertEquals(1, closed)
    }

    @Test
    fun `un cierre que falla no impide olvidar el recurso`() = runBlocking {
        val holder = ResourceHolder(dispatcher, { Engine() }, { error("ya cerrado") })
        holder.get(); holder.release()
        assertNull(holder.current())
    }

    @Test
    fun `se puede pedir crear de otra manera (respaldo en CPU)`() = runBlocking {
        val holder = ResourceHolder(dispatcher, { Engine() }, {})
        val cpu = Engine()
        assertSame(cpu, holder.get { cpu })
    }
}
