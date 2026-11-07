package com.eliasgonzalez.expensetracker.domain.repository

import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisQueueMetrics
import com.eliasgonzalez.expensetracker.domain.analysis.NotificationAnalysisQueueItem
import com.eliasgonzalez.expensetracker.domain.analysis.ReconciledTransaction
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionAnalysisInput
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionAnalysisResult

/** Persistencia local de notificaciones pendientes y auditoria de su resultado. */
interface NotificationAnalysisQueueRepository {
    /** null significa que el mismo evento ya esta encolado o fue procesado. */
    suspend fun enqueue(input: TransactionAnalysisInput): Long?

    suspend fun nextPending(): NotificationAnalysisQueueItem?
    suspend fun markProcessing(id: Long): Boolean
    suspend fun markCompleted(
        id: Long,
        result: TransactionAnalysisResult,
        reconciled: ReconciledTransaction,
    )
    suspend fun markFallback(id: Long, errorType: String)
    suspend fun markFailed(id: Long, errorType: String)
    suspend fun recoverProcessing(): Int
    suspend fun retryFailed(): Int
    suspend fun pruneTerminal(beforeTimestamp: Long): Int
    suspend fun metrics(): AnalysisQueueMetrics
}
