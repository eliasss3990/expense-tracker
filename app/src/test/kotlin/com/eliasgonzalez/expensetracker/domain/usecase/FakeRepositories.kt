package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** Dobles de prueba en memoria - sin Android, sin SQLite.
 *
 * Protegidos con Mutex igual que los Local*Repository reales (ver ese
 * comentario) - sin esto, estos fakes reproducirian el mismo lost update
 * y ningun test que los use podria detectarlo.
 *
 * `raceDelayMillis` es solo para tests de regresion: inserta una demora
 * entre leer la lista vieja y escribir la nueva, agrandando a proposito
 * la ventana de carrera para que dos escrituras lanzadas casi juntas
 * (`launch`/`async` bajo runTest) SIEMPRE interleaven de forma
 * deterministica en vez de depender de la suerte del scheduler. En 0
 * (default) no cambia nada del comportamiento normal.
 */

class FakeExpenseRepository(private val raceDelayMillis: Long = 0) : ExpenseRepository {
    private val nextId = AtomicLong(1)
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    override val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()
    private val mutex = Mutex()

    override suspend fun save(expense: Expense): Long = mutex.withLock {
        val id = nextId.getAndIncrement()
        val current = _expenses.value
        if (raceDelayMillis > 0) delay(raceDelayMillis)
        _expenses.value = listOf(expense.copy(id = id)) + current
        id
    }

    override suspend fun update(expense: Expense) = mutex.withLock {
        val current = _expenses.value
        if (raceDelayMillis > 0) delay(raceDelayMillis)
        _expenses.value = current.map { if (it.id == expense.id) expense else it }
        Unit
    }

    override suspend fun delete(id: Long) = mutex.withLock {
        val current = _expenses.value
        if (raceDelayMillis > 0) delay(raceDelayMillis)
        _expenses.value = current.filterNot { it.id == id }
        Unit
    }

    override fun findById(id: Long): Expense? = _expenses.value.find { it.id == id }
}

class FakeCandidateRepository(private val raceDelayMillis: Long = 0) : CandidateRepository {
    private val nextId = AtomicLong(1)
    private val _candidates = MutableStateFlow<List<ExpenseCandidate>>(emptyList())
    override val candidates: StateFlow<List<ExpenseCandidate>> = _candidates.asStateFlow()
    private val mutex = Mutex()

    override suspend fun save(candidate: ExpenseCandidate): Long = mutex.withLock {
        val id = nextId.getAndIncrement()
        val current = _candidates.value
        if (raceDelayMillis > 0) delay(raceDelayMillis)
        _candidates.value = listOf(candidate.copy(id = id)) + current
        id
    }

    override suspend fun update(candidate: ExpenseCandidate) = mutex.withLock {
        val current = _candidates.value
        if (raceDelayMillis > 0) delay(raceDelayMillis)
        _candidates.value = current.map { if (it.id == candidate.id) candidate else it }
        Unit
    }

    override fun findById(id: Long): ExpenseCandidate? = _candidates.value.find { it.id == id }
}

class FakeActivityRepository(private val raceDelayMillis: Long = 0) : ActivityRepository {
    private val nextId = AtomicLong(1)
    private val _recent = MutableStateFlow<List<ActivityEntry>>(emptyList())
    override val recent: StateFlow<List<ActivityEntry>> = _recent.asStateFlow()
    private val mutex = Mutex()

    override suspend fun record(entry: ActivityEntry) = mutex.withLock {
        val id = nextId.getAndIncrement()
        val current = _recent.value
        if (raceDelayMillis > 0) delay(raceDelayMillis)
        _recent.value = listOf(entry.copy(id = id)) + current
        Unit
    }
}
