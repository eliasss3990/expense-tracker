package com.eliasgonzalez.expensetracker.data.local

import android.content.ContentValues
import android.database.Cursor
import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.repository.ActivityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val RECENT_LIMIT = 100

class LocalActivityRepository(private val dbHelper: DbHelper) : ActivityRepository {

    private val _recent = MutableStateFlow<List<ActivityEntry>>(emptyList())
    override val recent: StateFlow<List<ActivityEntry>> = _recent.asStateFlow()

    // Ver el comentario equivalente en LocalExpenseRepository - este es
    // el repo mas expuesto al lost update, porque casi todos los casos
    // de uso llaman record() al final.
    private val mutex = Mutex()

    suspend fun hydrate() = withContext(Dispatchers.IO) {
        val loaded = mutableListOf<ActivityEntry>()
        dbHelper.readableDatabase.query(
            "activity_log", null, null, null, null, null,
            "timestamp DESC", RECENT_LIMIT.toString(),
        ).use { cursor -> while (cursor.moveToNext()) loaded.add(cursor.toActivityEntry()) }
        _recent.value = loaded
    }

    override suspend fun record(entry: ActivityEntry) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val id = dbHelper.writableDatabase.insert("activity_log", null, entry.toContentValues())
            _recent.value = (listOf(entry.copy(id = id)) + _recent.value).take(RECENT_LIMIT)
        }
    }

    private fun ActivityEntry.toContentValues() = ContentValues().apply {
        put("type", type.name)
        expenseId?.let { put("expense_id", it) } ?: putNull("expense_id")
        candidateId?.let { put("candidate_id", it) } ?: putNull("candidate_id")
        put("timestamp", timestamp)
        put("summary", summary)
    }

    private fun Cursor.toActivityEntry() = ActivityEntry(
        id = getLong(getColumnIndexOrThrow("id")),
        type = ActivityType.valueOf(getString(getColumnIndexOrThrow("type"))),
        expenseId = if (isNull(getColumnIndexOrThrow("expense_id"))) {
            null
        } else {
            getLong(getColumnIndexOrThrow("expense_id"))
        },
        candidateId = if (isNull(getColumnIndexOrThrow("candidate_id"))) {
            null
        } else {
            getLong(getColumnIndexOrThrow("candidate_id"))
        },
        timestamp = getLong(getColumnIndexOrThrow("timestamp")),
        summary = getString(getColumnIndexOrThrow("summary")),
    )
}
