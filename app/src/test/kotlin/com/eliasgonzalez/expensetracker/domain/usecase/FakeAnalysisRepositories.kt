package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisEvent
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisQueueRepository
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionAudit
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionAuditRepository
import com.eliasgonzalez.expensetracker.domain.analysis.QueuedAnalysis

class FakeAnalysisQueue : AnalysisQueueRepository {
    enum class State { PENDING, PROCESSING, DONE, GAVE_UP }

    class Row(val id: Long, val event: AnalysisEvent, var state: State = State.PENDING, var attempts: Int = 0) {
        var lastError: String? = null
    }

    val rows = mutableListOf<Row>()
    private var nextId = 1L

    fun state(id: Long) = rows.first { it.id == id }.state

    override suspend fun enqueue(event: AnalysisEvent): Long? {
        if (rows.any { it.event.key == event.key }) return null
        return nextId++.also { rows += Row(it, event) }
    }

    override suspend fun nextPending(excluded: Set<Long>, maxAttempts: Int): QueuedAnalysis? =
        rows.firstOrNull { it.state == State.PENDING && it.id !in excluded && it.attempts < maxAttempts }
            ?.let { QueuedAnalysis(it.id, it.event, it.attempts) }

    override suspend fun nextExhausted(maxAttempts: Int): QueuedAnalysis? =
        rows.firstOrNull { it.state == State.PENDING && it.attempts >= maxAttempts }
            ?.let { QueuedAnalysis(it.id, it.event, it.attempts) }

    override suspend fun markProcessing(id: Long): Boolean {
        val row = rows.first { it.id == id }
        if (row.state != State.PENDING) return false
        row.state = State.PROCESSING
        row.attempts++
        return true
    }

    override suspend fun markDone(id: Long) { rows.first { it.id == id }.state = State.DONE }

    override suspend fun markRetry(id: Long, errorClass: String, refundAttempt: Boolean) {
        val row = rows.first { it.id == id }
        row.state = State.PENDING
        row.lastError = errorClass
        if (refundAttempt) row.attempts--
    }

    override suspend fun markGaveUp(id: Long, errorClass: String) {
        val row = rows.first { it.id == id }
        row.state = State.GAVE_UP
        row.lastError = errorClass
    }

    override suspend fun recoverProcessing() {
        rows.filter { it.state == State.PROCESSING }.forEach { it.state = State.PENDING }
    }

    override suspend fun pruneBefore(timestamp: Long) {
        rows.removeAll { (it.state == State.DONE || it.state == State.GAVE_UP) && it.event.notification.timestamp < timestamp }
    }
}

class FakeDecisionAudit : DecisionAuditRepository {
    val entries = mutableListOf<DecisionAudit>()
    override suspend fun record(audit: DecisionAudit) { entries += audit }
    override suspend fun recent(limit: Int) = entries.takeLast(limit).reversed()
    override suspend fun pruneBefore(timestamp: Long) { entries.removeAll { it.decidedAt < timestamp } }
}
