package com.eliasgonzalez.expensetracker.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisRetrySchedulerTest {

    @Test
    fun `varios pedidos mientras uno espera se juntan en un solo drain`() = runTest {
        var drains = 0
        val s = AnalysisRetryScheduler(backgroundScope) { drains++ }
        s.schedule(1_000); s.schedule(1_000); s.schedule(5_000)
        advanceTimeBy(1_001)
        assertEquals(1, drains)
        advanceTimeBy(10_000)
        assertEquals("el tercer pedido (a 5 s) no debia sobrevivir", 1, drains)
    }

    @Test
    fun `un drain que pide el siguiente reintento DESDE adentro lo consigue (la cadena no se corta)`() = runTest {
        // Repro del review: el reintento se pide desde el drain que corre dentro del propio job.
        var drains = 0
        lateinit var s: AnalysisRetryScheduler
        s = AnalysisRetryScheduler(backgroundScope) { drains++; if (drains < 3) s.schedule(1_000) }
        s.schedule(1_000)
        advanceTimeBy(3_100)
        assertEquals("debia llegar al tercer intento", 3, drains)
    }

    @Test
    fun `despues de correr se puede volver a programar`() = runTest {
        var drains = 0
        val s = AnalysisRetryScheduler(backgroundScope) { drains++ }
        s.schedule(500); advanceTimeBy(600)
        s.schedule(500); advanceTimeBy(600)
        assertEquals(2, drains)
    }

    @Test
    fun `no corre antes de tiempo`() = runTest {
        var drains = 0
        AnalysisRetryScheduler(backgroundScope) { drains++ }.schedule(2_000)
        advanceTimeBy(1_900)
        assertEquals(0, drains)
    }
}
