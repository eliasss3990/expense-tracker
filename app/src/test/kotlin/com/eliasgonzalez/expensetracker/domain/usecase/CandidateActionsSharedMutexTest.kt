package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test de regresion (hallazgo de auditoria, 2026-08-28): antes,
 * ConfirmCandidate/EditCandidate/RejectCandidate/CreateCandidate tenian
 * cada uno su PROPIO Mutex privado - eso serializaba llamadas repetidas
 * al mismo caso de uso, pero no impedia que dos casos de uso DISTINTOS
 * operaran sobre el mismo candidato al mismo tiempo (ej. confirmar y
 * editar el mismo candidato desde dos lugares casi simultaneos), y las
 * dos terminaban registrando un Expense para el mismo candidato.
 *
 * Verificado revirtiendo el fix (instanciando ConfirmCandidate y
 * EditCandidate cada uno con su propio `Mutex()`, como antes, en vez de
 * uno compartido) - el test de abajo falla de forma determinista
 * (`gastosCreados` da 2, no 1). Con el mutex compartido (como hace
 * AppContainer ahora), pasa.
 */
class CandidateActionsSharedMutexTest {

    @Test
    fun `confirmar y editar el mismo candidato casi al mismo tiempo no duplica el gasto (mutex compartido)`() = runTest {
        val expenses = FakeExpenseRepository(raceDelayMillis = 10)
        val candidates = FakeCandidateRepository()
        val activity = FakeActivityRepository()
        val id = candidates.save(
            ExpenseCandidate(
                amount = 10_000,
                merchant = "Kiosco",
                categorySuggestion = "other",
                status = CandidateStatus.PENDING,
                occurredAt = 1L,
                detectedAt = 1L,
                sourceType = ExpenseSource.NOTIFICATION,
            )
        )

        // Mutex compartido entre los casos de uso de candidato (como hace
        // AppContainer) - este es el fix. RegisterExpense usa uno DISTINTO
        // (tambien como en AppContainer): comparte el mismo Mutex de
        // candidatos aca lo bloquearia entero, porque Mutex no es
        // reentrante en Kotlin.
        val candidateMutex = Mutex()
        val registerExpense = RegisterExpense(expenses, activity, Mutex())
        val confirmCandidate = ConfirmCandidate(candidates, registerExpense, activity, candidateMutex)
        val editCandidate = EditCandidate(candidates, registerExpense, activity, candidateMutex)

        val confirmResult = async { confirmCandidate(id) }
        val editResult = async { editCandidate(id, amount = 15_000, merchant = "Kiosco editado") }

        val results = listOf(confirmResult.await(), editResult.await())

        // Exactamente una de las dos acciones tuvo efecto (la otra vio el
        // candidato ya no-PENDING y devolvio null) - nunca las dos.
        assertEquals(1, results.count { it != null })
        assertEquals(1, expenses.expenses.value.size)
        assertTrue(candidates.findById(id)?.status != CandidateStatus.PENDING)
    }
}
