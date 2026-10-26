package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.catalog.CatalogCategoryPolicy
import com.eliasgonzalez.expensetracker.domain.catalog.CatalogDefaults
import com.eliasgonzalez.expensetracker.domain.catalog.ExpenseCatalogUsage
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.model.CategoryIds
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Antirregresion de los hallazgos del code-review del PR 2a:
 * - borrar una categoria vs. un gasto/candidato que se esta guardando
 *   justo ahora (mutex distintos => revisar "en uso" y borrar no eran
 *   atomicos y quedaba un dato apuntando a una categoria borrada);
 * - si un repositorio no cargo bien, no se opera sobre el catalogo.
 */
class CatalogRaceAndReadinessTest {

    private val kind = CatalogKind.CATEGORY
    private val propia = CatalogEntry(kind, "mascotas-1", "Mascotas", "#AA00FF", sortOrder = 99)
    private val catalog = FakeCatalogRepository(CatalogDefaults.categories + propia)
    private val activity = FakeActivityRepository()
    private val candidates = FakeCandidateRepository()

    // Mismos mutex separados que en AppContainer.
    private val catalogMutex = Mutex()
    private val candidateMutex = Mutex()
    private val expenseMutex = Mutex()

    private fun gasto(categoryId: String) = Expense(
        amount = 1_000, merchant = "x", categoryId = categoryId,
        occurredAt = 1, createdAt = 1, source = ExpenseSource.MANUAL,
    )

    private fun candidato(categoryId: String) = ExpenseCandidate(
        amount = 5_000, merchant = "Shell", occurredAt = 1, detectedAt = 1,
        sourceType = ExpenseSource.NOTIFICATION, categorySuggestion = categoryId,
        status = CandidateStatus.PENDING,
    )

    private fun rejected(o: CatalogOutcome) = (o as CatalogOutcome.Rejected).reason

    // --- borrar vs. escribir -------------------------------------------

    @Test
    fun `borrar una categoria mientras se guarda un gasto con ella no deja el gasto huerfano`() = runTest {
        val expenses = FakeExpenseRepository(raceDelayMillis = 50)
        val policy = CatalogCategoryPolicy(catalog)
        val register = RegisterExpense(expenses, activity, expenseMutex, policy)
        val delete = DeleteCatalogEntry(
            catalog, ExpenseCatalogUsage(expenses, candidates), catalogMutex,
            alsoLock = listOf(candidateMutex, expenseMutex),
        )

        val guardando = async { register(gasto(propia.id)) }
        runCurrent() // el gasto ya paso la politica y esta dentro del save demorado
        val borrando = async { delete(kind, propia.id) }

        val expenseId = guardando.await()
        val outcome = borrando.await()

        assertEquals(CatalogRejection.IN_USE, rejected(outcome))
        assertEquals(propia.id, expenses.findById(expenseId)!!.categoryId)
        assertNotNull(catalog.find(kind, propia.id))
    }

    @Test
    fun `borrar una categoria mientras se crea un candidato que la sugiere se bloquea`() = runTest {
        val slowCandidates = FakeCandidateRepository(raceDelayMillis = 50)
        val policy = CatalogCategoryPolicy(catalog)
        val create = CreateCandidate(slowCandidates, activity, candidateMutex, policy)
        val delete = DeleteCatalogEntry(
            catalog, ExpenseCatalogUsage(FakeExpenseRepository(), slowCandidates), catalogMutex,
            alsoLock = listOf(candidateMutex, expenseMutex),
        )

        val creando = async { create(candidato(propia.id)) }
        runCurrent()
        val borrando = async { delete(kind, propia.id) }

        val candidateId = creando.await()!!
        val outcome = borrando.await()

        assertEquals(CatalogRejection.IN_USE, rejected(outcome))
        assertEquals(propia.id, slowCandidates.findById(candidateId)!!.categorySuggestion)
        assertNotNull(catalog.find(kind, propia.id))
    }

    @Test
    fun `un parser que sugiere una categoria ya eliminada queda guardado como Otro`() = runTest {
        val create = CreateCandidate(candidates, activity, candidateMutex, CatalogCategoryPolicy(catalog))

        val id = create(candidato("categoria-que-ya-no-existe"))!!

        assertEquals(CategoryIds.OTHER, candidates.findById(id)!!.categorySuggestion)
    }

    @Test
    fun `un parser que sugiere una categoria DESACTIVADA conserva la sugerencia`() = runTest {
        catalog.save(catalog.find(kind, CategoryIds.FUEL)!!.copy(active = false))
        val create = CreateCandidate(candidates, activity, candidateMutex, CatalogCategoryPolicy(catalog))

        val id = create(candidato(CategoryIds.FUEL))!!

        assertEquals(CategoryIds.FUEL, candidates.findById(id)!!.categorySuggestion)
    }

    @Test
    fun `editar un candidato deja el candidato igual que el gasto que registra`() = runTest {
        val expenses = FakeExpenseRepository()
        val policy = CatalogCategoryPolicy(catalog)
        val register = RegisterExpense(expenses, activity, expenseMutex, policy)
        val editCandidate = EditCandidate(candidates, register, activity, candidateMutex, categoryPolicy = policy)
        val id = candidates.save(candidato(CategoryIds.FOOD))

        val expenseId = editCandidate(id, 6_000, "Shell", "id-inexistente")!!

        assertEquals(CategoryIds.OTHER, expenses.findById(expenseId)!!.categoryId)
        assertEquals(CategoryIds.OTHER, candidates.findById(id)!!.categorySuggestion)
    }

    // --- NOT_READY -----------------------------------------------------

    @Test
    fun `si un repositorio no cargo bien, ninguna operacion del catalogo escribe`() = runTest {
        val noListo: suspend () -> Boolean = { false }
        val antes = catalog.entries.value
        val create = CreateCatalogEntry(catalog, Mutex(), noListo) { "nuevo" }
        val rename = RenameCatalogEntry(catalog, Mutex(), noListo)
        val setActive = SetCatalogEntryActive(catalog, Mutex(), noListo)
        val delete = DeleteCatalogEntry(catalog, ExpenseCatalogUsage(FakeExpenseRepository(), candidates), Mutex(), noListo)

        assertEquals(CatalogRejection.NOT_READY, rejected(create(kind, "Nueva", "#AA00FF")))
        assertEquals(CatalogRejection.NOT_READY, rejected(rename(kind, propia.id, "Otro nombre")))
        assertEquals(CatalogRejection.NOT_READY, rejected(setActive(kind, propia.id, false)))
        assertEquals(CatalogRejection.NOT_READY, rejected(delete(kind, propia.id)))
        assertTrue("el catalogo no debe cambiar", catalog.entries.value == antes)
    }
}
