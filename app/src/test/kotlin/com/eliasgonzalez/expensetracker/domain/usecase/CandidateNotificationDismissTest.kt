package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.domain.notification.CandidateNotifier
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class SpyCandidateNotifier : CandidateNotifier {
    val dismissed = mutableListOf<Long>()
    override fun dismiss(candidateId: Long) {
        dismissed += candidateId
    }
}

/**
 * Regresion: la notificacion "Gasto detectado" quedaba colgada despues de
 * resolver el candidato por cualquier camino que no fuera Aceptar/Rechazar
 * desde la propia notificacion (Editar, o cualquier accion desde la
 * Bandeja). La regla ahora vive en los casos de uso: resuelto => se avisa
 * al CandidateNotifier, sin importar quien lo invoque.
 */
class CandidateNotificationDismissTest {

    private val candidates = FakeCandidateRepository()
    private val activity = FakeActivityRepository()
    private val notifier = SpyCandidateNotifier()
    private val mutex = Mutex()
    private val registerExpense = RegisterExpense(FakeExpenseRepository(), activity)
    private val confirm = ConfirmCandidate(candidates, registerExpense, activity, mutex, notifier)
    private val edit = EditCandidate(candidates, registerExpense, activity, mutex, notifier)
    private val reject = RejectCandidate(candidates, activity, mutex, notifier)

    private fun candidate(status: CandidateStatus = CandidateStatus.PENDING) = ExpenseCandidate(
        amount = 25_000,
        merchant = "Shell",
        occurredAt = 1L,
        detectedAt = 1L,
        sourceType = ExpenseSource.NOTIFICATION,
        status = status,
    )

    @Test
    fun `aceptar un candidato retira su notificacion`() = runTest {
        val id = candidates.save(candidate())

        confirm(id)

        assertEquals(listOf(id), notifier.dismissed)
    }

    @Test
    fun `editar un candidato retira su notificacion (antes quedaba colgada)`() = runTest {
        val id = candidates.save(candidate())

        edit(id, amount = 30_000, merchant = "Shell Ruta")

        assertEquals(listOf(id), notifier.dismissed)
    }

    @Test
    fun `rechazar un candidato retira su notificacion`() = runTest {
        val id = candidates.save(candidate())

        reject(id)

        assertEquals(listOf(id), notifier.dismissed)
    }

    @Test
    fun `solo retira la notificacion del candidato resuelto, no la de otros`() = runTest {
        val resuelto = candidates.save(candidate())
        candidates.save(candidate())

        confirm(resuelto)

        assertEquals(listOf(resuelto), notifier.dismissed)
    }

    @Test
    fun `no retira nada si el candidato no existe`() = runTest {
        confirm(999)
        edit(999, amount = 1, merchant = "x")
        reject(999)

        assertTrue(notifier.dismissed.isEmpty())
    }

    @Test
    fun `no retira nada si el candidato ya estaba resuelto (idempotencia)`() = runTest {
        val id = candidates.save(candidate(status = CandidateStatus.ACCEPTED))

        confirm(id)
        edit(id, amount = 1, merchant = "x")
        reject(id)

        assertTrue(notifier.dismissed.isEmpty())
    }

    @Test
    fun `resolver dos veces en paralelo retira la notificacion una sola vez`() = runTest {
        val id = candidates.save(candidate())

        listOf(
            async { confirm(id) },
            async { reject(id) },
        ).awaitAll()

        assertEquals(1, notifier.dismissed.size)
    }
}
