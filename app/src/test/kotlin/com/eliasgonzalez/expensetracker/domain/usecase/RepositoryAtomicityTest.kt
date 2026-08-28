package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test de regresion (hallazgo de auditoria, 2026-08-28) para el "lost
 * update" en el read-modify-write de los StateFlow en memoria de los
 * repositorios: dos escrituras casi simultaneas leian el mismo snapshot
 * viejo de la lista y una pisaba el update de la otra EN MEMORIA (aunque
 * en SQLite las dos filas quedaran bien guardadas).
 *
 * `raceDelayMillis` fuerza a proposito la ventana de carrera: bajo
 * runTest, N corrutinas lanzadas con `async` corren todas hasta su
 * primer `delay()` (todas leen la misma lista vieja) ANTES de que
 * avance el tiempo virtual y cualquiera llegue a escribir - sin el
 * Mutex que protege save()/record(), esto pierde N-1 de las N
 * escrituras de forma 100% deterministica, no es un test flaky.
 *
 * Verificado revirtiendo el fix (sacando `mutex.withLock` de
 * FakeExpenseRepository/FakeCandidateRepository/FakeActivityRepository.save()/
 * update()/record(), dejando el read-modify-write plano) - los 3 tests
 * fallan como se espera (ver detalle de cada uno). Con el fix restaurado,
 * los 3 pasan.
 */
class RepositoryAtomicityTest {

    @Test
    fun `N altas de gastos casi simultaneas no pierden ninguna (antes perdia N-1)`() = runTest {
        val repo = FakeExpenseRepository(raceDelayMillis = 10)
        val n = 20

        (1..n).map { i ->
            async {
                repo.save(
                    Expense(
                        amount = i.toLong(),
                        merchant = "Comercio $i",
                        occurredAt = 1L,
                        createdAt = 1L,
                        source = ExpenseSource.MANUAL,
                    )
                )
            }
        }.awaitAll()

        assertEquals(n, repo.expenses.value.size)
    }

    @Test
    fun `N altas de candidatos casi simultaneas no pierden ninguno`() = runTest {
        val repo = FakeCandidateRepository(raceDelayMillis = 10)
        val n = 20

        (1..n).map { i ->
            async {
                repo.save(
                    ExpenseCandidate(
                        amount = i.toLong(),
                        merchant = "Comercio $i",
                        categorySuggestion = "other",
                        status = CandidateStatus.PENDING,
                        occurredAt = 1L,
                        detectedAt = i.toLong(),
                        sourceType = ExpenseSource.NOTIFICATION,
                    )
                )
            }
        }.awaitAll()

        assertEquals(n, repo.candidates.value.size)
    }

    @Test
    fun `N entradas de actividad casi simultaneas no pierden ninguna - el repo mas expuesto, casi todos los casos de uso lo llaman`() = runTest {
        val repo = FakeActivityRepository(raceDelayMillis = 10)
        val n = 20

        (1..n).map { i ->
            async {
                repo.record(
                    ActivityEntry(
                        type = ActivityType.EXPENSE_CREATED,
                        expenseId = i.toLong(),
                        timestamp = i.toLong(),
                        summary = "entrada $i",
                    )
                )
            }
        }.awaitAll()

        assertEquals(n, repo.recent.value.size)
    }
}
