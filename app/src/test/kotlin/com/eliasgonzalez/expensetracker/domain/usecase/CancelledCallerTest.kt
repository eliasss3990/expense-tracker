package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.domain.notification.CandidateNotifier
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresion: resolver un gasto desde la BANDEJA no retiraba su notificacion (ni dejaba la
 * entrada en "Actividad"). La Bandeja lanza el caso de uso en `rememberCoroutineScope()`
 * de la tarjeta, y ese scope se CANCELA cuando la tarjeta sale de la pantalla: sale justo
 * cuando el cambio de estado saca al candidato de la lista de pendientes, es decir, en
 * medio del caso de uso, antes de `activity.record(...)` y `notifier.dismiss(...)`. Desde
 * la notificacion no pasaba: el receiver usa su propio scope, independiente de la UI.
 *
 * Estos tests cancelan al llamador JUSTO DESPUES del cambio de estado, como hace Compose, y
 * exigen que el caso de uso igual termine: un caso de uso que muta y registra no puede
 * quedar a medias porque la pantalla que lo lanzo ya no esta.
 */
class CancelledCallerTest {

    private val candidates = FakeCandidateRepository()
    private val expenses = FakeExpenseRepository()
    private val activity = FakeActivityRepository()
    private val expenseMutex = Mutex()
    private val candidateMutex = Mutex()

    private class SpyNotifier : CandidateNotifier {
        val dismissed = mutableListOf<Long>()
        override fun dismiss(candidateId: Long) {
            dismissed += candidateId
        }
    }

    /**
     * Imita a un repositorio real: cada llamada cruza `withContext(Dispatchers.IO)`, que
     * VERIFICA la cancelacion antes de seguir. `yield()` hace el mismo chequeo.
     */
    private class CancellationAwareActivity(private val delegate: ActivityRepository) : ActivityRepository {
        override val recent: StateFlow<List<ActivityEntry>> get() = delegate.recent
        override suspend fun record(entry: ActivityEntry) {
            yield()
            delegate.record(entry)
        }
    }

    /** Cancela al llamador justo despues de aplicar el cambio (la tarjeta/fila sale de la pantalla). */
    private class CancelAfterCandidateUpdate(private val delegate: CandidateRepository) :
        CandidateRepository by delegate {
        var caller: Job? = null
        override suspend fun update(candidate: ExpenseCandidate) {
            delegate.update(candidate)
            caller?.cancel()
        }
        override suspend fun save(candidate: ExpenseCandidate): Long =
            delegate.save(candidate).also { caller?.cancel() }
    }

    private class CancelAfterExpenseChange(private val delegate: ExpenseRepository) : ExpenseRepository by delegate {
        var caller: Job? = null
        override suspend fun save(expense: Expense): Long = delegate.save(expense).also { caller?.cancel() }
        override suspend fun update(expense: Expense) {
            delegate.update(expense)
            caller?.cancel()
        }
        override suspend fun delete(id: Long) {
            delegate.delete(id)
            caller?.cancel()
        }
    }

    private fun candidate() = ExpenseCandidate(
        amount = 25_000, merchant = "Shell", occurredAt = 1, detectedAt = 1,
        sourceType = ExpenseSource.NOTIFICATION, status = CandidateStatus.PENDING,
    )

    private fun gasto() = Expense(
        amount = 1_000, merchant = "Panadería", occurredAt = 1, createdAt = 1, source = ExpenseSource.MANUAL,
    )

    private fun tipos() = activity.recent.value.map { it.type }

    // --- candidatos (la Bandeja) -----------------------------------------------------

    @Test
    fun `aceptar desde la Bandeja registra la actividad y retira la notificacion aunque la tarjeta salga`() = runTest {
        val spy = SpyNotifier()
        val repo = CancelAfterCandidateUpdate(candidates)
        val register = RegisterExpense(expenses, activity, expenseMutex)
        val confirm = ConfirmCandidate(repo, register, CancellationAwareActivity(activity), candidateMutex, spy)
        val id = candidates.save(candidate())

        val caller = launch { confirm(id) }
        repo.caller = caller
        caller.join()
        assertTrue("el llamador debia haberse cancelado", caller.isCancelled)

        assertEquals(CandidateStatus.ACCEPTED, candidates.findById(id)!!.status)
        assertTrue("falta la entrada 'aceptado' en Actividad", ActivityType.CANDIDATE_ACCEPTED in tipos())
        assertEquals("la notificacion debia retirarse", listOf(id), spy.dismissed)
    }

    @Test
    fun `rechazar desde la Bandeja registra la actividad y retira la notificacion aunque la tarjeta salga`() = runTest {
        val spy = SpyNotifier()
        val repo = CancelAfterCandidateUpdate(candidates)
        val reject = RejectCandidate(repo, CancellationAwareActivity(activity), candidateMutex, spy)
        val id = candidates.save(candidate())

        val caller = launch { reject(id) }
        repo.caller = caller
        caller.join()
        assertTrue("el llamador debia haberse cancelado", caller.isCancelled)

        assertEquals(CandidateStatus.REJECTED, candidates.findById(id)!!.status)
        assertTrue(ActivityType.CANDIDATE_REJECTED in tipos())
        assertEquals(listOf(id), spy.dismissed)
    }

    @Test
    fun `editar desde la Bandeja registra la actividad y retira la notificacion aunque la tarjeta salga`() = runTest {
        val spy = SpyNotifier()
        val repo = CancelAfterCandidateUpdate(candidates)
        val register = RegisterExpense(expenses, activity, expenseMutex)
        val edit = EditCandidate(repo, register, CancellationAwareActivity(activity), candidateMutex, spy)
        val id = candidates.save(candidate())

        val caller = launch { edit(id, 30_000, "Shell Ruta") }
        repo.caller = caller
        caller.join()
        assertTrue("el llamador debia haberse cancelado", caller.isCancelled)

        assertEquals(CandidateStatus.EDITED, candidates.findById(id)!!.status)
        assertTrue(ActivityType.CANDIDATE_EDITED in tipos())
        assertEquals(listOf(id), spy.dismissed)
    }

    // --- gastos (el Dashboard) -------------------------------------------------------

    @Test
    fun `eliminar un gasto deja la entrada en Actividad aunque su fila salga de la pantalla`() = runTest {
        val repo = CancelAfterExpenseChange(expenses)
        val delete = DeleteExpense(repo, CancellationAwareActivity(activity), expenseMutex)
        val id = expenses.save(gasto())

        val caller = launch { delete(id) }
        repo.caller = caller
        caller.join()
        assertTrue("el llamador debia haberse cancelado", caller.isCancelled)

        assertNull(expenses.findById(id))
        assertTrue("falta la entrada 'eliminado' en Actividad", ActivityType.EXPENSE_DELETED in tipos())
    }

    @Test
    fun `editar un gasto deja la entrada en Actividad aunque el llamador se cancele`() = runTest {
        val repo = CancelAfterExpenseChange(expenses)
        val edit = EditExpense(repo, CancellationAwareActivity(activity), expenseMutex)
        val id = expenses.save(gasto())

        val caller = launch { edit(id, 2_000, "Panadería", "OTHER") }
        repo.caller = caller
        caller.join()
        assertTrue("el llamador debia haberse cancelado", caller.isCancelled)

        assertEquals(2_000L, expenses.findById(id)!!.amount)
        assertTrue(ActivityType.EXPENSE_EDITED in tipos())
    }

    @Test
    fun `registrar un gasto deja la entrada en Actividad aunque el llamador se cancele`() = runTest {
        val repo = CancelAfterExpenseChange(expenses)
        val register = RegisterExpense(repo, CancellationAwareActivity(activity), expenseMutex)

        val caller = launch { register(gasto()) }
        repo.caller = caller
        caller.join()
        assertTrue("el llamador debia haberse cancelado", caller.isCancelled)

        assertEquals(1, expenses.expenses.value.size)
        assertTrue(ActivityType.EXPENSE_CREATED in tipos())
    }

    // --- creacion de candidatos y borrado multiple -----------------------------------

    @Test
    fun `crear un candidato deja la entrada en Actividad aunque el llamador se cancele`() = runTest {
        val repo = CancelAfterCandidateUpdate(candidates)
        val create = CreateCandidate(repo, CancellationAwareActivity(activity), candidateMutex)

        val caller = launch { create(candidate()) }
        repo.caller = caller
        caller.join()

        assertTrue(caller.isCancelled)
        assertEquals(1, candidates.candidates.value.size)
        assertTrue(ActivityType.CANDIDATE_CREATED in tipos())
    }

    @Test
    fun `borrado multiple - cancelar tras el primero no deja el lote a medias`() = runTest {
        val repo = CancelAfterExpenseChange(expenses)
        val delete = DeleteExpense(repo, CancellationAwareActivity(activity), expenseMutex)
        val ids = List(3) { expenses.save(gasto()) }

        val caller = launch { delete.many(ids) }
        repo.caller = caller
        caller.join()

        assertTrue(caller.isCancelled)
        assertTrue("quedaron gastos sin borrar", expenses.expenses.value.isEmpty())
        assertEquals(3, tipos().count { it == ActivityType.EXPENSE_DELETED })
    }

    // --- el helper en si -------------------------------------------------------------

    @Test
    fun `un llamador ya cancelado no ejecuta el bloque aunque el mutex este libre`() = runTest {
        var ejecutado = false
        val cancelado = launch {
            // Cancelado justo antes de entrar, con el mutex libre (la via rapida de lock no lo nota).
            currentCoroutineContext().job.cancel()
            candidateMutex.withCommitLock { ejecutado = true }
        }
        cancelado.join()
        assertFalse("no debia ejecutarse para un llamador ya cancelado", ejecutado)
    }

    @Test
    fun `cancelado mientras espera el mutex ocupado sale sin ejecutar el bloque`() = runTest {
        var ejecutado = false
        candidateMutex.lock()
        val espera = launch { candidateMutex.withCommitLock { ejecutado = true } }
        yield()
        espera.cancelAndJoin()
        candidateMutex.unlock()

        assertTrue(espera.isCancelled)
        assertFalse(ejecutado)
    }

    @Test
    fun `una excepcion dentro del bloque libera el mutex`() = runTest {
        try {
            candidateMutex.withCommitLock { error("falla simulada") }
            fail("debia propagar la excepcion")
        } catch (e: IllegalStateException) {
            // esperado
        }
        assertFalse("el mutex quedo tomado", candidateMutex.isLocked)
    }

    // --- control: sin cancelacion todo sigue igual -----------------------------------

    @Test
    fun `control - sin cancelacion aceptar hace todo lo mismo`() = runTest {
        val spy = SpyNotifier()
        val register = RegisterExpense(expenses, activity, expenseMutex)
        val confirm = ConfirmCandidate(candidates, register, CancellationAwareActivity(activity), candidateMutex, spy)
        val id = candidates.save(candidate())

        confirm(id)

        assertTrue(ActivityType.CANDIDATE_ACCEPTED in tipos() && ActivityType.EXPENSE_CREATED in tipos())
        assertEquals(listOf(id), spy.dismissed)
    }
}
