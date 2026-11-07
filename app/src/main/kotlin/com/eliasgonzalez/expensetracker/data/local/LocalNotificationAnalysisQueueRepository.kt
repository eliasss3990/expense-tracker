package com.eliasgonzalez.expensetracker.data.local

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.eliasgonzalez.expensetracker.domain.analysis.NotificationAnalysisQueueItem
import com.eliasgonzalez.expensetracker.domain.analysis.NotificationSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.ParserSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.ReconciledTransaction
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionAnalysisInput
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionAnalysisResult
import com.eliasgonzalez.expensetracker.domain.repository.NotificationAnalysisQueueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class LocalNotificationAnalysisQueueRepository(
    private val dbHelper: DbHelper,
) : NotificationAnalysisQueueRepository {

    override suspend fun enqueue(input: TransactionAnalysisInput): Long? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val eventKey = eventKey(input.notification)
        val values = ContentValues().apply {
            put("event_key", eventKey)
            put("package_name", input.notification.packageName)
            put("application_name", input.notification.applicationName)
            put("title", input.notification.title)
            put("notification_text", input.notification.text)
            put("big_text", input.notification.bigText)
            put("posted_at", input.notification.timestamp)
            val parser = input.parserResult
            if (parser == null) {
                putNull("parser_amount")
                putNull("parser_currency")
                putNull("parser_merchant")
                putNull("parser_merchant_confident")
                putNull("parser_id")
                putNull("parser_confidence")
            } else {
                put("parser_amount", parser.amount)
                put("parser_currency", parser.currency)
                put("parser_merchant", parser.merchant)
                put("parser_merchant_confident", if (parser.merchantConfident) 1 else 0)
                put("parser_id", parser.parserId)
                put("parser_confidence", parser.confidence)
            }
            put("status", STATUS_PENDING)
            put("attempts", 0)
            put("created_at", now)
            put("updated_at", now)
        }

        val db = dbHelper.writableDatabase
        val insertedId = db.insertWithOnConflict(
            TABLE,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        val resultId = if (insertedId != -1L) {
            insertedId
        } else {
            // Si una notificacion habia quedado FAILED, permitir reintentarla
            // al llegar un nuevo evento identico o al instalar el modelo.
            val existing = db.query(
                TABLE,
                arrayOf("id", "status"),
                "event_key = ?",
                arrayOf(eventKey),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) to cursor.getString(1) else null
            }
            if (existing != null && existing.second == STATUS_FAILED) {
                db.update(
                    TABLE,
                    values,
                    "id = ?",
                    arrayOf(existing.first.toString()),
                )
                existing.first
            } else {
                -1L
            }
        }
        resultId.takeIf { it > 0L }
    }

    override suspend fun nextPending(): NotificationAnalysisQueueItem? = withContext(Dispatchers.IO) {
        dbHelper.readableDatabase.query(
            TABLE,
            null,
            "status = ?",
            arrayOf(STATUS_PENDING),
            null,
            null,
            "created_at ASC, id ASC",
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toQueueItem() else null
        }
    }

    override suspend fun markProcessing(id: Long): Boolean = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.compileStatement(
            "UPDATE $TABLE SET status = ?, attempts = attempts + 1, updated_at = ? " +
                "WHERE id = ? AND status = ?"
        ).use { statement ->
            statement.bindString(1, STATUS_PROCESSING)
            statement.bindLong(2, System.currentTimeMillis())
            statement.bindLong(3, id)
            statement.bindString(4, STATUS_PENDING)
            statement.executeUpdateDelete() > 0
        }
    }

    override suspend fun markCompleted(
        id: Long,
        result: TransactionAnalysisResult,
        reconciled: ReconciledTransaction,
    ): Unit = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.update(
            TABLE,
            ContentValues().apply {
                put("status", STATUS_COMPLETED)
                put("model_version", result.modelVersion)
                put("decision", reconciled.decision.name)
                put("movement", reconciled.movement.name)
                put("result_amount", reconciled.amount)
                put("result_currency", reconciled.currency)
                put("result_merchant", reconciled.merchant)
                put("occurred_at", reconciled.occurredAt)
                put("reasons", reconciled.reasons.sorted().joinToString(","))
                putNull("last_error")
                put("updated_at", System.currentTimeMillis())
            },
            "id = ?",
            arrayOf(id.toString()),
        )
        Unit
    }

    override suspend fun markFallback(id: Long, errorType: String) = withContext(Dispatchers.IO) {
        updateStatus(id, STATUS_FALLBACK, errorType)
    }

    override suspend fun markFailed(id: Long, errorType: String) = withContext(Dispatchers.IO) {
        updateStatus(id, STATUS_FAILED, errorType)
    }

    override suspend fun recoverProcessing(): Int = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.update(
            TABLE,
            ContentValues().apply {
                put("status", STATUS_PENDING)
                put("updated_at", System.currentTimeMillis())
            },
            "status = ?",
            arrayOf(STATUS_PROCESSING),
        )
    }

    override suspend fun retryFailed(): Int = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.update(
            TABLE,
            ContentValues().apply {
                put("status", STATUS_PENDING)
                putNull("last_error")
                put("updated_at", System.currentTimeMillis())
            },
            "status = ?",
            arrayOf(STATUS_FAILED),
        )
    }

    override suspend fun pruneTerminal(beforeTimestamp: Long): Int = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete(
            TABLE,
            "status IN (?, ?, ?) AND updated_at < ?",
            arrayOf(STATUS_COMPLETED, STATUS_FALLBACK, STATUS_FAILED, beforeTimestamp.toString()),
        )
    }

    override suspend fun metrics(): AnalysisQueueMetrics = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        fun count(selection: String, args: Array<String>): Long =
            db.query(TABLE, arrayOf("COUNT(*)"), selection, args, null, null, null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }

        AnalysisQueueMetrics(
            pending = count("status = ?", arrayOf(STATUS_PENDING)),
            processing = count("status = ?", arrayOf(STATUS_PROCESSING)),
            completed = count("status = ?", arrayOf(STATUS_COMPLETED)),
            fallback = count("status = ?", arrayOf(STATUS_FALLBACK)),
            failed = count("status = ?", arrayOf(STATUS_FAILED)),
            autoRegisterSuggested = count(
                "status = ? AND decision = ?",
                arrayOf(STATUS_COMPLETED, "AUTO_REGISTER"),
            ),
            reviewSuggested = count(
                "status = ? AND decision = ?",
                arrayOf(STATUS_COMPLETED, "REQUIRES_REVIEW"),
            ),
            rejected = count(
                "status = ? AND decision = ?",
                arrayOf(STATUS_COMPLETED, "REJECT"),
            ),
        )
    }

    private fun updateStatus(id: Long, status: String, errorType: String) {
        dbHelper.writableDatabase.update(
            TABLE,
            ContentValues().apply {
                put("status", status)
                put("last_error", errorType.take(120))
                put("updated_at", System.currentTimeMillis())
            },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    private fun Cursor.toQueueItem(): NotificationAnalysisQueueItem {
        val parserAmountIndex = getColumnIndexOrThrow("parser_amount")
        val parser = if (isNull(parserAmountIndex)) {
            null
        } else {
            ParserSnapshot(
                amount = getLong(parserAmountIndex),
                currency = getString(getColumnIndexOrThrow("parser_currency")),
                merchant = getString(getColumnIndexOrThrow("parser_merchant")),
                merchantConfident = getInt(getColumnIndexOrThrow("parser_merchant_confident")) != 0,
                parserId = getString(getColumnIndexOrThrow("parser_id")),
                confidence = getDouble(getColumnIndexOrThrow("parser_confidence")),
            )
        }
        return NotificationAnalysisQueueItem(
            id = getLong(getColumnIndexOrThrow("id")),
            input = TransactionAnalysisInput(
                notification = NotificationSnapshot(
                    packageName = getString(getColumnIndexOrThrow("package_name")),
                    applicationName = getString(getColumnIndexOrThrow("application_name")),
                    title = getString(getColumnIndexOrThrow("title")),
                    text = getString(getColumnIndexOrThrow("notification_text")),
                    bigText = getString(getColumnIndexOrThrow("big_text")),
                    timestamp = getLong(getColumnIndexOrThrow("posted_at")),
                ),
                parserResult = parser,
            ),
            attempts = getInt(getColumnIndexOrThrow("attempts")),
        )
    }

    private fun eventKey(notification: NotificationSnapshot): String {
        val source = listOf(
            notification.packageName,
            notification.timestamp.toString(),
            notification.title,
            notification.text,
            notification.bigText,
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        const val TABLE = "notification_analysis_queue"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FALLBACK = "FALLBACK"
        const val STATUS_FAILED = "FAILED"
    }
}
