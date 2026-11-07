package com.eliasgonzalez.expensetracker.data.local

import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisEvent
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisQueueRepository
import com.eliasgonzalez.expensetracker.domain.analysis.NotificationSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.ParserEvidence
import com.eliasgonzalez.expensetracker.domain.analysis.ParserSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.QueuedAnalysis
import com.eliasgonzalez.expensetracker.domain.analysis.SourceTrust
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val PENDING = "PENDING"
private const val PROCESSING = "PROCESSING"
private const val DONE = "DONE"
private const val GAVE_UP = "GAVE_UP"

internal class LocalAnalysisQueueRepository(
    private val db: SqlDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : AnalysisQueueRepository {

    private val mutex = Mutex()

    override suspend fun enqueue(event: AnalysisEvent): Long? = io {
        val exists = db.query("SELECT 1 AS x FROM analysis_queue WHERE event_key = ?", listOf(event.key)) { it.long("x") }
        if (exists.isNotEmpty()) return@io null
        val parsed = (event.parser as? ParserEvidence.Parsed)?.snapshot
        val ignored = event.parser as? ParserEvidence.ExplicitlyIgnored
        val n = event.notification
        val ts = now()
        db.insert(
            """INSERT INTO analysis_queue (event_key, package_name, application_name, title, text, big_text,
                notified_at, parser_kind, parser_id, parser_amount, parser_currency, parser_merchant,
                parser_merchant_confident, trust, state, attempts, enqueued_at, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '$PENDING', 0, ?, ?)""",
            listOf(
                event.key, n.packageName, n.applicationName, n.title, n.text, n.bigText, n.timestamp,
                when (event.parser) {
                    is ParserEvidence.Parsed -> "PARSED"
                    is ParserEvidence.ExplicitlyIgnored -> "IGNORED"
                    ParserEvidence.NoParser -> "NONE"
                },
                parsed?.parserId ?: ignored?.parserId, parsed?.amount, parsed?.currency, parsed?.merchant,
                parsed?.merchantConfident?.let { if (it) 1 else 0 },
                event.trust.name, ts, ts,
            ),
        )
    }

    override suspend fun nextPending(excluded: Set<Long>, maxAttempts: Int): QueuedAnalysis? = io {
        val notIn = if (excluded.isEmpty()) "" else " AND id NOT IN (${excluded.joinToString(",") { "?" }})"
        db.query(
            "SELECT * FROM analysis_queue WHERE state = '$PENDING' AND attempts < ?$notIn ORDER BY id LIMIT 1",
            listOf<Any?>(maxAttempts) + excluded.toList(),
        ) { row -> QueuedAnalysis(row.long("id"), row.toEvent(), row.long("attempts").toInt()) }.firstOrNull()
    }

    override suspend fun markProcessing(id: Long): Boolean = io {
        db.update(
            "UPDATE analysis_queue SET state = '$PROCESSING', attempts = attempts + 1, updated_at = ? " +
                "WHERE id = ? AND state = '$PENDING'",
            listOf(now(), id),
        ) == 1
    }

    override suspend fun markDone(id: Long) = setState(id, DONE, null, 0)

    override suspend fun markRetry(id: Long, errorClass: String, refundAttempt: Boolean) =
        setState(id, PENDING, errorClass, if (refundAttempt) -1 else 0)

    override suspend fun markGaveUp(id: Long, errorClass: String) = setState(id, GAVE_UP, errorClass, 0)

    private suspend fun setState(id: Long, state: String, error: String?, attemptsDelta: Int) {
        io {
            db.update(
                "UPDATE analysis_queue SET state = ?, last_error = ?, attempts = MAX(0, attempts + ?), updated_at = ? WHERE id = ?",
                listOf(state, error, attemptsDelta, now(), id),
            )
        }
    }

    override suspend fun recoverProcessing() {
        io { db.update("UPDATE analysis_queue SET state = '$PENDING', updated_at = ? WHERE state = '$PROCESSING'", listOf(now())) }
    }

    override suspend fun pruneBefore(timestamp: Long) {
        io {
            db.update(
                "DELETE FROM analysis_queue WHERE state IN ('$DONE', '$GAVE_UP') AND updated_at < ?",
                listOf(timestamp),
            )
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }

    private fun SqlRow.toEvent(): AnalysisEvent {
        val parser = when (string("parser_kind")) {
            "PARSED" -> ParserEvidence.Parsed(
                ParserSnapshot(
                    amount = long("parser_amount"),
                    currency = string("parser_currency"),
                    merchant = string("parser_merchant"),
                    merchantConfident = long("parser_merchant_confident") == 1L,
                    parserId = string("parser_id"),
                )
            )
            "IGNORED" -> ParserEvidence.ExplicitlyIgnored(string("parser_id"))
            else -> ParserEvidence.NoParser
        }
        return AnalysisEvent(
            key = string("event_key"),
            notification = NotificationSnapshot(
                packageName = string("package_name"),
                applicationName = string("application_name"),
                title = string("title"),
                text = string("text"),
                bigText = string("big_text"),
                timestamp = long("notified_at"),
            ),
            parser = parser,
            trust = SourceTrust.valueOf(string("trust")),
        )
    }
}
