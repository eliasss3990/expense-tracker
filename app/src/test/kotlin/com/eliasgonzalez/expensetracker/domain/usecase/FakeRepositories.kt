package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** Dobles de prueba en memoria - sin Android, sin SQLite. */

class FakeExpenseRepository : ExpenseRepository {
    private val nextId = AtomicLong(1)
    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    override val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    override suspend fun save(expense: Expense): Long {
        val id = nextId.getAndIncrement()
        _expenses.value = listOf(expense.copy(id = id)) + _expenses.value
        return id
    }

    override suspend fun delete(id: Long) {
        _expenses.value = _expenses.value.filterNot { it.id == id }
    }
}

class FakeCandidateRepository : CandidateRepository {
    private val nextId = AtomicLong(1)
    private val _candidates = MutableStateFlow<List<ExpenseCandidate>>(emptyList())
    override val candidates: StateFlow<List<ExpenseCandidate>> = _candidates.asStateFlow()

    override suspend fun save(candidate: ExpenseCandidate): Long {
        val id = nextId.getAndIncrement()
        _candidates.value = listOf(candidate.copy(id = id)) + _candidates.value
        return id
    }

    override suspend fun update(candidate: ExpenseCandidate) {
        _candidates.value = _candidates.value.map { if (it.id == candidate.id) candidate else it }
    }

    override fun findById(id: Long): ExpenseCandidate? = _candidates.value.find { it.id == id }
}

class FakeActivityRepository : ActivityRepository {
    private val nextId = AtomicLong(1)
    private val _recent = MutableStateFlow<List<ActivityEntry>>(emptyList())
    override val recent: StateFlow<List<ActivityEntry>> = _recent.asStateFlow()

    override suspend fun record(entry: ActivityEntry) {
        val id = nextId.getAndIncrement()
        _recent.value = listOf(entry.copy(id = id)) + _recent.value
    }
}
