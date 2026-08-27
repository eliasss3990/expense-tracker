package com.eliasgonzalez.expensetracker.domain.repository

import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import kotlinx.coroutines.flow.StateFlow

interface CandidateRepository {
    val candidates: StateFlow<List<ExpenseCandidate>>
    suspend fun save(candidate: ExpenseCandidate): Long
    suspend fun update(candidate: ExpenseCandidate)
    fun findById(id: Long): ExpenseCandidate?
}
