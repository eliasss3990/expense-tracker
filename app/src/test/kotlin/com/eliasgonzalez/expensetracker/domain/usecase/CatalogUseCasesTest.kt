package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.catalog.CatalogDefaults
import com.eliasgonzalez.expensetracker.domain.catalog.CatalogUsage
import com.eliasgonzalez.expensetracker.domain.catalog.ExpenseCatalogUsage
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.model.Category
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogUseCasesTest {

    private val kind = CatalogKind.CATEGORY
    private val catalog = FakeCatalogRepository(CatalogDefaults.categories)
    private val expenses = FakeExpenseRepository()
    private val candidates = FakeCandidateRepository()
    private val mutex = Mutex()
    private var seq = 0
    private val create = CreateCatalogEntry(catalog, mutex) { "nuevo-${++seq}" }
    private val rename = RenameCatalogEntry(catalog, mutex)
    private val setActive = SetCatalogEntryActive(catalog, mutex)
    private val delete = DeleteCatalogEntry(catalog, ExpenseCatalogUsage(expenses, candidates), mutex)
    private val observe = ObserveCatalog(catalog)

    private fun rejected(o: CatalogOutcome) = (o as CatalogOutcome.Rejected).reason
    private fun created(o: CatalogOutcome) = (o as CatalogOutcome.Success).entry!!

    private suspend fun gastoCon(categoryId: String) {
        expenses.save(
            Expense(
                amount = 1_000, merchant = "x", categoryId = categoryId,
                occurredAt = 1, createdAt = 1, source = ExpenseSource.MANUAL,
            )
        )
    }

    // --- crear ---------------------------------------------------------

    @Test
    fun `crear agrega una entrada activa, no de fabrica, al final del orden`() = runTest {
        val entry = created(create(kind, "Mascotas", "#aa00ff"))

        assertEquals("Mascotas", entry.label)
        assertEquals("#AA00FF", entry.color)
        assertTrue(entry.active)
        assertTrue(!entry.builtIn && !entry.protected)
        assertEquals(CatalogDefaults.categories.maxOf { it.sortOrder } + 1, entry.sortOrder)
        assertEquals(entry, catalog.find(kind, entry.id))
    }

    @Test
    fun `el id no se deriva del nombre y es distinto para cada alta`() = runTest {
        val a = created(create(kind, "Mascotas", "#AA00FF"))
        val b = created(create(kind, "Regalos", "#AA00FF"))

        assertNotEquals(a.id, b.id)
        assertTrue(!a.id.contains("Mascotas", ignoreCase = true))
    }

    @Test
    fun `normaliza espacios del nombre`() = runTest {
        assertEquals("Salidas con amigos", created(create(kind, "  Salidas   con  amigos ", "#AA00FF")).label)
    }

    @Test
    fun `rechaza nombre vacio, solo espacios o demasiado largo`() = runTest {
        assertEquals(CatalogRejection.INVALID_LABEL, rejected(create(kind, "", "#AA00FF")))
        assertEquals(CatalogRejection.INVALID_LABEL, rejected(create(kind, "   ", "#AA00FF")))
        assertEquals(CatalogRejection.INVALID_LABEL, rejected(create(kind, "x".repeat(41), "#AA00FF")))
    }

    @Test
    fun `rechaza color que no sea hex de 6 digitos`() = runTest {
        listOf("", "red", "#FFF", "AA00FF", "#GG00FF", "#AA00FF00").forEach {
            assertEquals(it, CatalogRejection.INVALID_COLOR, rejected(create(kind, "Mascotas", it)))
        }
    }

    @Test
    fun `rechaza nombre duplicado sin importar mayusculas, incluso contra las de fabrica`() = runTest {
        assertEquals(CatalogRejection.DUPLICATE_LABEL, rejected(create(kind, "comida", "#AA00FF")))
        created(create(kind, "Mascotas", "#AA00FF"))
        assertEquals(CatalogRejection.DUPLICATE_LABEL, rejected(create(kind, "MASCOTAS", "#AA00FF")))
    }

    @Test
    fun `el mismo nombre en otro tipo de catalogo no es duplicado (el catalogo es reutilizable)`() = runTest {
        val dosTipos = CreateCatalogEntry(catalog, mutex, validKinds = setOf(kind, "payment_method")) { "m-${++seq}" }

        val outcome = dosTipos("payment_method", "Comida", "#AA00FF")

        assertTrue(outcome is CatalogOutcome.Success)
        assertEquals(1, catalog.entries.value.count { it.kind == kind && it.label == "Comida" })
    }

    @Test
    fun `dos altas simultaneas con el mismo nombre crean una sola`() = runTest {
        val results = listOf(
            async { create(kind, "Mascotas", "#AA00FF") },
            async { create(kind, "mascotas", "#AA00FF") },
        ).awaitAll()

        assertEquals(1, results.count { it is CatalogOutcome.Success })
        assertEquals(1, catalog.entries.value.count { it.label.equals("mascotas", true) })
    }

    // --- renombrar -----------------------------------------------------

    @Test
    fun `renombrar cambia el nombre y conserva el id`() = runTest {
        val outcome = rename(kind, Category.FOOD.id, "Restaurantes")

        assertEquals("Restaurantes", created(outcome).label)
        assertEquals(Category.FOOD.id, created(outcome).id)
        assertEquals("Restaurantes", catalog.find(kind, Category.FOOD.id)?.label)
    }

    @Test
    fun `renombrar no afecta a los gastos que ya usan esa categoria`() = runTest {
        gastoCon(Category.FOOD.id)

        rename(kind, Category.FOOD.id, "Restaurantes")

        assertEquals(Category.FOOD.id, expenses.expenses.value.single().categoryId)
    }

    @Test
    fun `renombrar rechaza inexistente, nombre invalido y nombre de otra entrada`() = runTest {
        assertEquals(CatalogRejection.NOT_FOUND, rejected(rename(kind, "no-existe", "X")))
        assertEquals(CatalogRejection.INVALID_LABEL, rejected(rename(kind, Category.FOOD.id, " ")))
        assertEquals(CatalogRejection.DUPLICATE_LABEL, rejected(rename(kind, Category.FOOD.id, "combustible")))
    }

    @Test
    fun `renombrar a su propio nombre cambiando mayusculas es valido`() = runTest {
        assertEquals("COMIDA", created(rename(kind, Category.FOOD.id, "COMIDA")).label)
    }

    // --- desactivar / reactivar ----------------------------------------

    @Test
    fun `desactivar saca la entrada de los selectores y reactivar la devuelve`() = runTest {
        setActive(kind, Category.FUEL.id, false)
        assertTrue(observe(kind).first().none { it.id == Category.FUEL.id })

        setActive(kind, Category.FUEL.id, true)
        assertTrue(observe(kind).first().any { it.id == Category.FUEL.id })
    }

    @Test
    fun `la protegida no se puede desactivar pero reactivarla siempre se permite`() = runTest {
        assertEquals(CatalogRejection.PROTECTED, rejected(setActive(kind, Category.OTHER.id, false)))
        assertTrue(catalog.find(kind, Category.OTHER.id)!!.active)
        assertTrue(setActive(kind, Category.OTHER.id, true) is CatalogOutcome.Success)
    }

    @Test
    fun `desactivar rechaza una entrada inexistente`() = runTest {
        assertEquals(CatalogRejection.NOT_FOUND, rejected(setActive(kind, "no-existe", false)))
    }

    @Test
    fun `desactivar una categoria con gastos no toca los gastos`() = runTest {
        gastoCon(Category.FUEL.id)

        setActive(kind, Category.FUEL.id, false)

        assertEquals(Category.FUEL.id, expenses.expenses.value.single().categoryId)
        assertEquals("Combustible", catalog.find(kind, Category.FUEL.id)?.label)
    }

    // --- eliminar ------------------------------------------------------

    @Test
    fun `elimina de verdad una entrada sin gastos`() = runTest {
        val entry = created(create(kind, "Mascotas", "#AA00FF"))

        assertTrue(delete(kind, entry.id) is CatalogOutcome.Success)
        assertNull(catalog.find(kind, entry.id))
    }

    @Test
    fun `no elimina una categoria con gastos - solo se puede desactivar`() = runTest {
        gastoCon(Category.FOOD.id)

        assertEquals(CatalogRejection.IN_USE, rejected(delete(kind, Category.FOOD.id)))
        assertEquals("Comida", catalog.find(kind, Category.FOOD.id)?.label)
    }

    @Test
    fun `desactivada con gastos sigue sin poder eliminarse`() = runTest {
        gastoCon(Category.FOOD.id)
        setActive(kind, Category.FOOD.id, false)

        assertEquals(CatalogRejection.IN_USE, rejected(delete(kind, Category.FOOD.id)))
    }

    @Test
    fun `una vez borrados sus gastos, la categoria si se puede eliminar`() = runTest {
        gastoCon(Category.EDUCATION.id)
        expenses.delete(expenses.expenses.value.single().id)

        assertTrue(delete(kind, Category.EDUCATION.id) is CatalogOutcome.Success)
    }

    @Test
    fun `no elimina la protegida ni una inexistente`() = runTest {
        assertEquals(CatalogRejection.PROTECTED, rejected(delete(kind, Category.OTHER.id)))
        assertEquals(CatalogRejection.NOT_FOUND, rejected(delete(kind, "no-existe")))
    }

    @Test
    fun `eliminar usa el criterio de uso inyectado, no uno fijo de categorias`() = runTest {
        val enUso = CatalogUsage { _, id -> id == "medio-1" }
        val deleteGenerico = DeleteCatalogEntry(catalog, enUso, mutex)
        catalog.save(CatalogEntry("payment_method", "medio-1", "Efectivo", "#112233"))

        assertEquals(CatalogRejection.IN_USE, rejected(deleteGenerico("payment_method", "medio-1")))
    }

    @Test
    fun `ExpenseCatalogUsage solo conoce categorias - otros tipos nunca estan en uso`() = runTest {
        gastoCon("medio-1")

        assertTrue(!ExpenseCatalogUsage(expenses, candidates).isInUse("payment_method", "medio-1"))
    }

    // --- observar ------------------------------------------------------

    @Test
    fun `observar filtra por tipo y ordena por sortOrder`() = runTest {
        catalog.save(CatalogEntry("payment_method", "m1", "Efectivo", "#112233", sortOrder = 0))
        catalog.save(CatalogEntry(kind, "z", "Zeta", "#112233", sortOrder = -5))

        val ids = observe(kind).first().map { it.id }

        assertEquals("z", ids.first())
        assertTrue(ids.none { it == "m1" })
    }

    @Test
    fun `observar con onlyActive=false incluye las desactivadas (para filtros y listados)`() = runTest {
        setActive(kind, Category.HEALTH.id, false)

        assertTrue(observe(kind, onlyActive = true).first().none { it.id == Category.HEALTH.id })
        assertTrue(observe(kind, onlyActive = false).first().any { it.id == Category.HEALTH.id })
    }

    // --- antirregresion de los hallazgos del code-review ----------------

    @Test
    fun `rechaza un tipo de catalogo desconocido en vez de guardar una entrada huerfana`() = runTest {
        assertEquals(CatalogRejection.INVALID_KIND, rejected(create("categoria", "Mascotas", "#AA00FF")))
        assertTrue(catalog.entries.value.none { it.label == "Mascotas" })
    }

    @Test
    fun `las operaciones de escritura esperan a que el catalogo este hidratado`() = runTest {
        val listo = CompletableDeferred<Unit>()
        val vacio = FakeCatalogRepository()
        val createEsperando = CreateCatalogEntry(vacio, Mutex(), { listo.await() }) { "id-1" }
        val deleteEsperando = DeleteCatalogEntry(vacio, { _, _ -> false }, Mutex(), { listo.await() })

        val alta = async { createEsperando(kind, "Mascotas", "#AA00FF") }
        val baja = async { deleteEsperando(kind, "id-1") }
        runCurrent()

        assertTrue("no debe validar contra una lista aun sin hidratar", !alta.isCompleted && !baja.isCompleted)

        listo.complete(Unit)
        assertTrue(alta.await() is CatalogOutcome.Success)
        assertTrue(baja.await() is CatalogOutcome.Success)
    }

    @Test
    fun `renombrar y desactivar tambien esperan a la hidratacion`() = runTest {
        val listo = CompletableDeferred<Unit>()
        val repo = FakeCatalogRepository(CatalogDefaults.categories)
        val renameEsperando = RenameCatalogEntry(repo, Mutex()) { listo.await() }
        val activeEsperando = SetCatalogEntryActive(repo, Mutex()) { listo.await() }

        val a = async { renameEsperando(kind, Category.FOOD.id, "Restaurantes") }
        val b = async { activeEsperando(kind, Category.FUEL.id, false) }
        runCurrent()

        assertTrue(!a.isCompleted && !b.isCompleted)
        assertEquals("Comida", repo.find(kind, Category.FOOD.id)?.label)

        listo.complete(Unit)
        a.await(); b.await()
        assertEquals("Restaurantes", repo.find(kind, Category.FOOD.id)?.label)
    }

    // --- candidatos pendientes: desactivar no cambia nada, borrar se bloquea ---

    private suspend fun candidato(categoryId: String, status: CandidateStatus) = candidates.save(
        ExpenseCandidate(
            amount = 1_000, merchant = "x", occurredAt = 1, detectedAt = 1,
            sourceType = ExpenseSource.NOTIFICATION, categorySuggestion = categoryId, status = status,
        )
    )

    @Test
    fun `no elimina una categoria que un candidato pendiente sugiere - solo se puede desactivar`() = runTest {
        candidato(Category.FUEL.id, CandidateStatus.PENDING)

        assertEquals(CatalogRejection.IN_USE, rejected(delete(kind, Category.FUEL.id)))
    }

    @Test
    fun `un candidato ya resuelto no impide eliminar la categoria`() = runTest {
        val creada = created(create(kind, "Mascotas", "#AA00FF"))
        candidato(creada.id, CandidateStatus.REJECTED)
        candidato(creada.id, CandidateStatus.ACCEPTED)

        assertTrue(delete(kind, creada.id) is CatalogOutcome.Success)
    }

    @Test
    fun `desactivar una categoria no cambia la categoria de gastos ni de candidatos pendientes`() = runTest {
        gastoCon(Category.FUEL.id)
        val candidatoId = candidato(Category.FUEL.id, CandidateStatus.PENDING)

        setActive(kind, Category.FUEL.id, false)

        assertEquals(Category.FUEL.id, expenses.expenses.value.single().categoryId)
        assertEquals(Category.FUEL.id, candidates.findById(candidatoId)!!.categorySuggestion)
    }
}
