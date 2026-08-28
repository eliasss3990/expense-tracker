package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DEDUP_WINDOW_MILLIS = 5 * 60 * 1000L

class CreateCandidateTest {

    private fun candidate(
        merchant: String,
        detectedAt: Long,
        amount: Long = 85_000,
        merchantConfident: Boolean = true,
        currency: String = "PYG",
    ) = ExpenseCandidate(
        amount = amount,
        merchant = merchant,
        merchantConfident = merchantConfident,
        occurredAt = detectedAt,
        detectedAt = detectedAt,
        sourceType = ExpenseSource.NOTIFICATION,
        currency = currency,
    )

    @Test
    fun `crea el candidato cuando no hay duplicado`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        val id = createCandidate(candidate("McDonalds", detectedAt = 1000L))

        assertNotNull(id)
        assertEquals(1, candidates.candidates.value.size)
    }

    @Test
    fun `no duplica cuando el banco y la billetera avisan la misma compra`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("MCDONALDS", detectedAt = 1_000L))
        val secondId = createCandidate(candidate("McDonald's", detectedAt = 1_000L + 60_000L))

        assertNull(secondId)
        assertEquals(1, candidates.candidates.value.size)
    }

    @Test
    fun `si pasa la ventana de tiempo no lo considera duplicado`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("MCDONALDS", detectedAt = 0L))
        val secondId = createCandidate(candidate("MCDONALDS", detectedAt = 10 * 60_000L))

        assertNotNull(secondId)
        assertEquals(2, candidates.candidates.value.size)
    }

    @Test
    fun `distinto monto no se considera duplicado`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("MCDONALDS", detectedAt = 0L, amount = 85_000))
        val secondId = createCandidate(candidate("MCDONALDS", detectedAt = 1_000L, amount = 40_000))

        assertNotNull(secondId)
        assertEquals(2, candidates.candidates.value.size)
    }

    @Test
    fun `no duplica cuando una de las fuentes no confia en el comercio - ueno app + mail`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        // Notificación push del banco: sin beneficiario, merchant de relleno.
        createCandidate(
            candidate("Transferencia Ueno Bank", detectedAt = 0L, amount = 800_000, merchantConfident = false)
        )
        // Mail de confirmación, con el beneficiario real - mismo monto, casi mismo instante.
        val secondId = candidate("ROBERTO GONZALEZ QUIONEZ", detectedAt = 500L, amount = 800_000)
            .let { createCandidate(it) }

        assertNull(secondId)
        assertEquals(1, candidates.candidates.value.size)
    }

    @Test
    fun `dos comercios confiables pero distintos no se funden aunque una fuente sea debil en otro campo`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("Kiosco", detectedAt = 0L, amount = 25_000))
        val secondId = createCandidate(candidate("Farmacia", detectedAt = 1_000L, amount = 25_000))

        assertNotNull(secondId)
        assertEquals(2, candidates.candidates.value.size)
    }

    @Test
    fun `dos notificaciones casi simultaneas no crean dos candidatos - condicion de carrera`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        val first = async { createCandidate(candidate("GONZALEZ MEZA", detectedAt = 1_000L)) }
        val second = async { createCandidate(candidate("GONZALEZ MEZA", detectedAt = 1_010L)) }

        val results = listOfNotNull(first.await(), second.await())
        assertEquals(1, results.size)
        assertEquals(1, candidates.candidates.value.size)
    }

    // ----- Bordes exactos de DEDUP_WINDOW_MILLIS -----

    @Test
    fun `justo en el limite de la ventana - exactamente 5 minutos - todavia dedupea`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("MCDONALDS", detectedAt = 0L))
        val secondId = createCandidate(candidate("MCDONALDS", detectedAt = DEDUP_WINDOW_MILLIS))

        assertNull(secondId)
        assertEquals(1, candidates.candidates.value.size)
    }

    @Test
    fun `un milisegundo antes del limite dedupea`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("MCDONALDS", detectedAt = 0L))
        val secondId = createCandidate(candidate("MCDONALDS", detectedAt = DEDUP_WINDOW_MILLIS - 1))

        assertNull(secondId)
        assertEquals(1, candidates.candidates.value.size)
    }

    @Test
    fun `un milisegundo despues del limite ya no dedupea`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("MCDONALDS", detectedAt = 0L))
        val secondId = createCandidate(candidate("MCDONALDS", detectedAt = DEDUP_WINDOW_MILLIS + 1))

        assertNotNull(secondId)
        assertEquals(2, candidates.candidates.value.size)
    }

    @Test
    fun `el limite tambien aplica cuando el segundo llega antes en el tiempo - orden inverso`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        // El primero detectado "despues" en el reloj, el segundo "antes" - abs() debe cubrir ambos sentidos.
        createCandidate(candidate("MCDONALDS", detectedAt = 10_000_000L))
        val secondId = createCandidate(
            candidate("MCDONALDS", detectedAt = 10_000_000L - (DEDUP_WINDOW_MILLIS + 1))
        )

        assertNotNull(secondId)
        assertEquals(2, candidates.candidates.value.size)
    }

    // ----- Combinaciones de merchantConfident -----

    @Test
    fun `ambos confident con comercio distinto no dedupea aunque monto y moneda coincidan`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("Kiosco Don Juan", detectedAt = 0L, amount = 50_000, merchantConfident = true))
        val secondId = createCandidate(
            candidate("Farmacia Catedral", detectedAt = 1_000L, amount = 50_000, merchantConfident = true)
        )

        assertNotNull(secondId)
        assertEquals(2, candidates.candidates.value.size)
    }

    @Test
    fun `ambos no confident con comercios distintos igual dedupea por monto y moneda`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("APP DE BILLETERA", detectedAt = 0L, amount = 120_000, merchantConfident = false))
        val secondId = createCandidate(
            candidate("Notificacion Push", detectedAt = 2_000L, amount = 120_000, merchantConfident = false)
        )

        assertNull(secondId)
        assertEquals(1, candidates.candidates.value.size)
    }

    @Test
    fun `uno confident y el otro no dedupea sin exigir coincidencia de comercio`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("Relleno Generico", detectedAt = 0L, amount = 99_000, merchantConfident = false))
        val secondId = createCandidate(
            candidate("Supermercado Real SA", detectedAt = 3_000L, amount = 99_000, merchantConfident = true)
        )

        assertNull(secondId)
        assertEquals(1, candidates.candidates.value.size)
    }

    @Test
    fun `normalizacion de comercio ignora mayusculas espacios y puntuacion`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("  mc-donald's  ", detectedAt = 0L, amount = 70_000, merchantConfident = true))
        val secondId = createCandidate(
            candidate("MCDONALDS", detectedAt = 1_000L, amount = 70_000, merchantConfident = true)
        )

        assertNull(secondId)
        assertEquals(1, candidates.candidates.value.size)
    }

    @Test
    fun `normalizacion no borra diferencias reales entre comercios distintos`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("Farmacia Central", detectedAt = 0L, amount = 30_000, merchantConfident = true))
        val secondId = createCandidate(
            candidate("Farmacia Catedral", detectedAt = 1_000L, amount = 30_000, merchantConfident = true)
        )

        assertNotNull(secondId)
        assertEquals(2, candidates.candidates.value.size)
    }

    // ----- Moneda -----

    @Test
    fun `mismo monto pero distinta moneda no dedupea`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("MCDONALDS", detectedAt = 0L, amount = 100_000, currency = "PYG"))
        val secondId = createCandidate(
            candidate("MCDONALDS", detectedAt = 500L, amount = 100_000, currency = "USD")
        )

        assertNotNull(secondId)
        assertEquals(2, candidates.candidates.value.size)
    }

    // ----- Timestamps extremos -----

    @Test
    fun `timestamps negativos dentro de la ventana dedupean`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("MCDONALDS", detectedAt = -1_000L))
        val secondId = createCandidate(candidate("MCDONALDS", detectedAt = -500L))

        assertNull(secondId)
        assertEquals(1, candidates.candidates.value.size)
    }

    @Test
    fun `timestamp cero y timestamp negativo fuera de ventana no dedupean`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("MCDONALDS", detectedAt = 0L))
        val secondId = createCandidate(candidate("MCDONALDS", detectedAt = -(DEDUP_WINDOW_MILLIS + 1)))

        assertNotNull(secondId)
        assertEquals(2, candidates.candidates.value.size)
    }

    @Test
    fun `timestamps muy futuros y muy pasados no dedupean ni rompen la comparacion`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        // Distancia enorme entre ambos - abs(existing - candidate) no debe overflowear con Long.
        createCandidate(candidate("MCDONALDS", detectedAt = Long.MIN_VALUE / 2))
        val secondId = createCandidate(candidate("MCDONALDS", detectedAt = Long.MAX_VALUE / 2))

        assertNotNull(secondId)
        assertEquals(2, candidates.candidates.value.size)
    }

    @Test
    fun `timestamps extremos identicos igual dedupean`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        createCandidate(candidate("MCDONALDS", detectedAt = Long.MAX_VALUE))
        val secondId = createCandidate(candidate("MCDONALDS", detectedAt = Long.MAX_VALUE))

        assertNull(secondId)
        assertEquals(1, candidates.candidates.value.size)
    }

    // ----- Concurrencia real: N corrutinas, mezcla de duplicados y no-duplicados -----

    @Test
    fun `N corrutinas concurrentes creando el mismo candidato solo sobrevive una`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        val n = 20
        val jobs = (0 until n).map { i ->
            async { createCandidate(candidate("GONZALEZ MEZA", detectedAt = 1_000L + i)) }
        }
        val results = jobs.awaitAll()

        assertEquals(1, results.filterNotNull().size)
        assertEquals(1, candidates.candidates.value.size)
    }

    @Test
    fun `N corrutinas concurrentes con datos distintos - no deberian dedupear entre si`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        val n = 20
        val jobs = (0 until n).map { i ->
            async {
                createCandidate(
                    candidate("Comercio $i", detectedAt = i * (DEDUP_WINDOW_MILLIS + 1), amount = 1_000L + i)
                )
            }
        }
        val results = jobs.awaitAll()

        assertEquals(n, results.filterNotNull().size)
        assertEquals(n, candidates.candidates.value.size)
    }

    @Test
    fun `mezcla concurrente de duplicados y no duplicados entremezclados - conteo final exacto`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        // 4 grupos de 5 candidatos "casi simultaneos" cada uno (mismo monto/comercio/moneda,
        // dentro de la ventana): cada grupo debe colapsar a exactamente 1 sobreviviente.
        // Los grupos entre si estan separados ampliamente en el tiempo y tienen datos distintos,
        // asi que no deben cruzarse entre ellos.
        val groupCount = 4
        val perGroup = 5
        val groupSpacing = DEDUP_WINDOW_MILLIS * 10

        val jobs = (0 until groupCount).flatMap { g ->
            (0 until perGroup).map { i ->
                async {
                    createCandidate(
                        candidate(
                            merchant = "Comercio Grupo $g",
                            detectedAt = g * groupSpacing + i, // dentro de la ventana del grupo
                            amount = 10_000L + g,
                        )
                    )
                }
            }
        }
        val results = jobs.awaitAll()

        assertEquals(groupCount, results.filterNotNull().size)
        assertEquals(groupCount, candidates.candidates.value.size)
    }

    @Test
    fun `alta concurrencia - todas las corrutinas terminan y el conteo nunca supera lo esperado`() = runTest {
        val candidates = FakeCandidateRepository()
        val createCandidate = CreateCandidate(candidates, FakeActivityRepository())

        // 15 intentos del mismo candidato compitiendo de verdad por el mutex.
        val n = 15
        val jobs = (0 until n).map {
            async { createCandidate(candidate("PANIFICADORA LA ESPIGA", detectedAt = 5_000L)) }
        }
        val results = jobs.awaitAll()

        assertTrue("A lo sumo un resultado no nulo esperado, hubo ${results.count { it != null }}", results.count { it != null } == 1)
        assertEquals(1, candidates.candidates.value.size)
    }
}
