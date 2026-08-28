package com.eliasgonzalez.expensetracker.domain.repository

import com.eliasgonzalez.expensetracker.domain.model.Expense
import kotlinx.coroutines.flow.StateFlow

interface ExpenseRepository {
    val expenses: StateFlow<List<Expense>>
    suspend fun save(expense: Expense): Long
    suspend fun update(expense: Expense)
    suspend fun delete(id: Long)
    fun findById(id: Long): Expense?
}
