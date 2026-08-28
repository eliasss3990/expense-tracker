package com.eliasgonzalez.expensetracker.data.local

import android.content.ContentValues
import android.database.Cursor
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LocalExpenseRepository(private val dbHelper: DbHelper) : ExpenseRepository {

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    override val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    suspend fun hydrate() = withContext(Dispatchers.IO) {
        val loaded = mutableListOf<Expense>()
        dbHelper.readableDatabase.query(
            "expenses", null, null, null, null, null, "occurred_at DESC"
        ).use { cursor -> while (cursor.moveToNext()) loaded.add(cursor.toExpense()) }
        _expenses.value = loaded
    }

    override suspend fun save(expense: Expense): Long = withContext(Dispatchers.IO) {
        val id = dbHelper.writableDatabase.insert("expenses", null, expense.toContentValues())
        _expenses.value = listOf(expense.copy(id = id)) + _expenses.value
        id
    }

    override suspend fun update(expense: Expense) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.update(
            "expenses", expense.toContentValues(), "id = ?", arrayOf(expense.id.toString())
        )
        _expenses.value = _expenses.value.map { if (it.id == expense.id) expense else it }
        Unit
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete("expenses", "id = ?", arrayOf(id.toString()))
        _expenses.value = _expenses.value.filterNot { it.id == id }
        Unit
    }

    override fun findById(id: Long): Expense? = _expenses.value.find { it.id == id }

    private fun Expense.toContentValues() = ContentValues().apply {
        put("amount", amount)
        put("currency", currency)
        put("merchant", merchant)
        put("category_id", categoryId)
        put("description", description)
        put("occurred_at", occurredAt)
        put("created_at", createdAt)
        put("source", source.name)
        sourceReference?.let { put("source_reference", it) } ?: putNull("source_reference")
    }

    private fun Cursor.toExpense() = Expense(
        id = getLong(getColumnIndexOrThrow("id")),
        amount = getLong(getColumnIndexOrThrow("amount")),
        currency = getString(getColumnIndexOrThrow("currency")),
        merchant = getString(getColumnIndexOrThrow("merchant")),
        categoryId = getString(getColumnIndexOrThrow("category_id")),
        description = getString(getColumnIndexOrThrow("description")),
        occurredAt = getLong(getColumnIndexOrThrow("occurred_at")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        source = ExpenseSource.valueOf(getString(getColumnIndexOrThrow("source"))),
        sourceReference = if (isNull(getColumnIndexOrThrow("source_reference"))) {
            null
        } else {
            getLong(getColumnIndexOrThrow("source_reference"))
        },
    )
}
