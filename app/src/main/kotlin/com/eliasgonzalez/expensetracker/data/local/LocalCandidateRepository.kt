package com.eliasgonzalez.expensetracker.data.local

import android.content.ContentValues
import android.database.Cursor
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalCandidateRepository(private val dbHelper: DbHelper) : CandidateRepository {

    private val _candidates = MutableStateFlow<List<ExpenseCandidate>>(emptyList())
    override val candidates: StateFlow<List<ExpenseCandidate>> = _candidates.asStateFlow()

    // Ver el comentario equivalente en LocalExpenseRepository - mismo
    // lost update real sin este mutex.
    private val mutex = Mutex()

    suspend fun hydrate() = withContext(Dispatchers.IO) {
        val loaded = mutableListOf<ExpenseCandidate>()
        dbHelper.readableDatabase.query(
            "candidates", null, null, null, null, null, "detected_at DESC"
        ).use { cursor -> while (cursor.moveToNext()) loaded.add(cursor.toCandidate()) }
        _candidates.value = loaded
    }

    override suspend fun save(candidate: ExpenseCandidate): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            val id = dbHelper.writableDatabase.insert("candidates", null, candidate.toContentValues())
            _candidates.value = listOf(candidate.copy(id = id)) + _candidates.value
            id
        }
    }

    override suspend fun update(candidate: ExpenseCandidate) = withContext(Dispatchers.IO) {
        mutex.withLock {
            dbHelper.writableDatabase.update(
                "candidates", candidate.toContentValues(), "id = ?", arrayOf(candidate.id.toString())
            )
            _candidates.value = _candidates.value.map { if (it.id == candidate.id) candidate else it }
        }
    }

    override fun findById(id: Long): ExpenseCandidate? = _candidates.value.find { it.id == id }

    private fun ExpenseCandidate.toContentValues() = ContentValues().apply {
        put("amount", amount)
        put("currency", currency)
        put("merchant", merchant)
        put("category_suggestion", categorySuggestion)
        put("occurred_at", occurredAt)
        put("detected_at", detectedAt)
        put("source_type", sourceType.name)
        put("source_app", sourceApp)
        put("parser_id", parserId)
        put("confidence", confidence)
        put("status", status.name)
    }

    private fun Cursor.toCandidate() = ExpenseCandidate(
        id = getLong(getColumnIndexOrThrow("id")),
        amount = getLong(getColumnIndexOrThrow("amount")),
        currency = getString(getColumnIndexOrThrow("currency")),
        merchant = getString(getColumnIndexOrThrow("merchant")),
        categorySuggestion = getString(getColumnIndexOrThrow("category_suggestion")),
        occurredAt = getLong(getColumnIndexOrThrow("occurred_at")),
        detectedAt = getLong(getColumnIndexOrThrow("detected_at")),
        sourceType = ExpenseSource.valueOf(getString(getColumnIndexOrThrow("source_type"))),
        sourceApp = getString(getColumnIndexOrThrow("source_app")),
        parserId = getString(getColumnIndexOrThrow("parser_id")),
        confidence = getDouble(getColumnIndexOrThrow("confidence")),
        status = CandidateStatus.valueOf(getString(getColumnIndexOrThrow("status"))),
    )
}
