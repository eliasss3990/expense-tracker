package com.eliasgonzalez.expensetracker.data

import androidx.compose.runtime.mutableStateListOf
import com.eliasgonzalez.expensetracker.model.CandidateStatus
import com.eliasgonzalez.expensetracker.model.ExpenseCandidate
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory store for the POC. Fase 1 real usará Room + ExpenseRepository;
 * acá alcanza con demostrar el flujo Candidate -> Confirm -> Expense.
 */
object CandidateStore {
    private val nextId = AtomicLong(1)
    val candidates = mutableStateListOf<ExpenseCandidate>()

    fun newId(): Long = nextId.getAndIncrement()

    fun add(candidate: ExpenseCandidate) {
        candidates.add(0, candidate)
    }

    fun findById(id: Long): ExpenseCandidate? = candidates.find { it.id == id }

    fun updateStatus(id: Long, status: CandidateStatus) {
        findById(id)?.status = status
    }

    fun pending() = candidates.filter { it.status == CandidateStatus.PENDING }

    fun confirmed() = candidates.filter {
        it.status == CandidateStatus.ACCEPTED || it.status == CandidateStatus.EDITED
    }
}
