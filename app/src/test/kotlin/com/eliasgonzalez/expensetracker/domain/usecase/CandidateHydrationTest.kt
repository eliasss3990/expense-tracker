package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.domain.notification.CandidateNotifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Antirregresion: en un proceso recien arrancado (ej. el usuario toca "Aceptar"
 * o "Editar" en una notificacion colgada tras actualizar la app) la lista de
 * candidatos todavia esta vacia. Sin esperar la carga el candidato "no existe",
 * se retiraba la notificacion y la accion se perdia en silencio.
 */
class CandidateHydrationTest {

    private class SpyNotifier : CandidateNotifier {
        val dismissed = mutableListOf<Long>()
        override fun dismiss(candidateId: Long) { dismissed += candidateId }
    }

    private val candidates = FakeCandidateRepository()
    private val activity = FakeActivityRepository()
    private val notifier = SpyNotifier()
    private val candidateMutex = Mutex()
    private val register = RegisterExpense(FakeExpenseRepository(), activity, Mutex())

    private fun candidato() = ExpenseCandidate(
        amount = 5_000, merchant = "Shell", occurredAt = 1, detectedAt = 1,
        sourceType = ExpenseSource.NOTIFICATION, status = CandidateStatus.PENDING,
    )

    @Test
    fun `aceptar espera a que termine la carga y recien ahi encuentra el candidato`() = runTest {
        val gate = CompletableDeferred<Boolean>()
        val confirm = ConfirmCandidate(candidates, register, activity, candidateMutex, notifier) { gate.await() }

        val resultado = async { confirm(1L) } // el candidato todavia no esta en la lista
        runCurrent()
        assertTrue("no debe resolver con la lista vacia", !resultado.isCompleted)

        val id = candidates.save(candidato()) // la carga termina y aparece
        gate.complete(true)

        assertNotNull(resultado.await())
        assertEquals(CandidateStatus.ACCEPTED, candidates.findById(id)!!.status)
        assertEquals(listOf(id), notifier.dismissed)
    }

    @Test
    fun `si la carga fallo, aceptar no hace nada y NO retira la notificacion (queda para reintentar)`() = runTest {
        val id = candidates.save(candidato())
        val confirm = ConfirmCandidate(candidates, register, activity, candidateMutex, notifier) { false }

        assertNull(confirm(id))

        assertEquals(CandidateStatus.PENDING, candidates.findById(id)!!.status)
        assertTrue(notifier.dismissed.isEmpty())
    }

    @Test
    fun `rechazar espera la carga y si fallo no toca nada ni retira la notificacion`() = runTest {
        val gate = CompletableDeferred<Boolean>()
        val reject = RejectCandidate(candidates, activity, candidateMutex, notifier) { gate.await() }
        val resultado = async { reject(1L) }
        runCurrent()
        assertTrue(!resultado.isCompleted)
        val id = candidates.save(candidato())
        gate.complete(true)
        resultado.await()
        assertEquals(CandidateStatus.REJECTED, candidates.findById(id)!!.status)

        val otro = candidates.save(candidato())
        val rejectFallido = RejectCandidate(candidates, activity, candidateMutex, notifier) { false }
        notifier.dismissed.clear()
        rejectFallido(otro)
        assertEquals(CandidateStatus.PENDING, candidates.findById(otro)!!.status)
        assertTrue(notifier.dismissed.isEmpty())
    }

    @Test
    fun `editar espera la carga y si fallo no registra gasto ni retira la notificacion`() = runTest {
        val gate = CompletableDeferred<Boolean>()
        val edit = EditCandidate(candidates, register, activity, candidateMutex, notifier, awaitReady = { gate.await() })
        val resultado = async { edit(1L, 6_000, "Shell") }
        runCurrent()
        assertTrue(!resultado.isCompleted)
        val id = candidates.save(candidato())
        gate.complete(true)
        assertNotNull(resultado.await())
        assertEquals(CandidateStatus.EDITED, candidates.findById(id)!!.status)

        val otro = candidates.save(candidato())
        notifier.dismissed.clear()
        val editFallido = EditCandidate(candidates, register, activity, candidateMutex, notifier, awaitReady = { false })
        assertNull(editFallido(otro, 1_000, "x"))
        assertEquals(CandidateStatus.PENDING, candidates.findById(otro)!!.status)
        assertTrue(notifier.dismissed.isEmpty())
    }

    @Test
    fun `crear un candidato espera la carga y, si fallo, igual lo guarda (mejor duplicado que perdido)`() = runTest {
        val gate = CompletableDeferred<Boolean>()
        val create = CreateCandidate(candidates, activity, candidateMutex, awaitReady = { gate.await() })
        val resultado = async { create(candidato()) }
        runCurrent()
        assertTrue(!resultado.isCompleted)
        gate.complete(true)
        assertNotNull(resultado.await())

        val createFallido = CreateCandidate(FakeCandidateRepository(), activity, Mutex(), awaitReady = { false })
        assertNotNull("un gasto detectado no se pierde aunque la carga haya fallado", createFallido(candidato()))
    }
}
