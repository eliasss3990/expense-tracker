package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisDecision
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisMode
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisModeProvider
import com.eliasgonzalez.expensetracker.domain.analysis.AnalyzedTransaction
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionAnalysisInput
import com.eliasgonzalez.expensetracker.domain.analysis.AnalyzeTransaction
import com.eliasgonzalez.expensetracker.domain.model.CategoryIds
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.domain.notification.CandidateAlertPublisher
import com.eliasgonzalez.expensetracker.domain.repository.NotificationAnalysisQueueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Procesa la cola en serie. El registro directo y la creacion de candidatos son
 * idempotentes/deduplicados, por lo que un PROCESSING recuperado tras la muerte
 * del proceso puede ejecutarse de nuevo sin duplicar el gasto.
 */
class ProcessNotificationAnalysisQueue(
    private val queue: NotificationAnalysisQueueRepository,
    private val analyzeTransaction: AnalyzeTransaction,
    private val createCandidate: CreateCandidate,
    private val findPendingCandidate: FindPendingCandidate,
    private val registerAnalyzedExpense: RegisterAnalyzedExpense,
    private val alertPublisher: CandidateAlertPublisher,
    private val modeProvider: AnalysisModeProvider,
    private val appScope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val drainMutex = Mutex()

    fun enqueue(input: TransactionAnalysisInput) {
        appScope.launch {
            val id = queue.enqueue(input)
            if (id != null) drain()
        }
    }

    fun recoverOnStartup(retryFailed: Boolean) {
        appScope.launch {
            queue.recoverProcessing()
            if (retryFailed) queue.retryFailed()
            queue.pruneTerminal(now() - RETENTION_MILLIS)
            drain()
        }
    }

    fun retryFailed() {
        appScope.launch {
            queue.retryFailed()
            drain()
        }
    }

    private suspend fun drain() = drainMutex.withLock {
        while (true) {
            val item = queue.nextPending() ?: break
            if (!queue.markProcessing(item.id)) continue

            val analyzed = try {
                analyzeTransaction(item.input)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                handleAnalyzerFailure(item.id, item.input)
                continue
            }

            try {
                when (modeProvider.currentMode()) {
                    AnalysisMode.SHADOW -> {
                        // Modo inicial: registrar metricas de IA, pero mantener el
                        // comportamiento anterior sin auto-registros ni nuevos candidatos.
                        publishLegacyCandidate(item.input)
                    }
                    AnalysisMode.ASSISTED -> {
                        when (analyzed.reconciled.decision) {
                            AnalysisDecision.AUTO_REGISTER,
                            AnalysisDecision.REQUIRES_REVIEW -> publishReviewCandidate(item.input, analyzed)
                            AnalysisDecision.REJECT -> Unit
                        }
                    }
                    AnalysisMode.AUTOMATIC -> {
                        when (analyzed.reconciled.decision) {
                            AnalysisDecision.AUTO_REGISTER ->
                                registerAnalyzedExpense(item.input.notification, analyzed.reconciled)
                            AnalysisDecision.REQUIRES_REVIEW -> publishReviewCandidate(item.input, analyzed)
                            AnalysisDecision.REJECT -> Unit
                        }
                    }
                }
                queue.markCompleted(item.id, analyzed.modelResult, analyzed.reconciled)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                queue.markFailed(item.id, error.javaClass.simpleName)
            }
        }
    }

    private suspend fun handleAnalyzerFailure(id: Long, input: TransactionAnalysisInput) {
        val parser = input.parserResult
        if (parser == null) {
            queue.markFailed(id, "AnalyzerUnavailable")
            return
        }

        try {
            val candidate = ExpenseCandidate(
                amount = parser.amount,
                currency = parser.currency,
                merchant = parser.merchant,
                merchantConfident = parser.merchantConfident,
                occurredAt = input.notification.timestamp,
                detectedAt = input.notification.timestamp,
                sourceType = ExpenseSource.NOTIFICATION,
                sourceApp = input.notification.applicationName,
                parserId = parser.parserId,
                confidence = parser.confidence,
            )
            publishCandidate(candidate)
            queue.markFallback(id, "AnalyzerUnavailable")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            queue.markFailed(id, error.javaClass.simpleName)
        }
    }

    private suspend fun publishLegacyCandidate(input: TransactionAnalysisInput) {
        val parser = input.parserResult ?: return
        publishCandidate(
            ExpenseCandidate(
                amount = parser.amount,
                currency = parser.currency,
                merchant = parser.merchant,
                merchantConfident = parser.merchantConfident,
                occurredAt = input.notification.timestamp,
                detectedAt = input.notification.timestamp,
                sourceType = ExpenseSource.NOTIFICATION,
                sourceApp = input.notification.applicationName,
                parserId = parser.parserId,
                confidence = parser.confidence,
            )
        )
    }

    private suspend fun publishReviewCandidate(
        input: TransactionAnalysisInput,
        analyzed: AnalyzedTransaction,
    ) {
        val transaction = analyzed.reconciled
        val parser = input.parserResult
        val amount = if ("AMOUNT_CONFLICT" in transaction.reasons) parser?.amount else transaction.amount
        val currency = if ("CURRENCY_CONFLICT" in transaction.reasons) parser?.currency else transaction.currency
        val merchant = if ("MERCHANT_CONFLICT" in transaction.reasons) parser?.merchant else transaction.merchant

        val candidate = ExpenseCandidate(
            amount = amount?.takeIf { it > 0 } ?: return,
            currency = currency?.takeIf { it.matches(Regex("[A-Z]{3}")) } ?: return,
            merchant = merchant?.takeIf(String::isNotBlank) ?: input.notification.applicationName,
            merchantConfident = if ("MERCHANT_CONFLICT" in transaction.reasons) {
                parser?.merchantConfident == true
            } else {
                parser?.merchantConfident == true || "MERCHANT_EVIDENCE_MISSING" !in transaction.reasons
            },
            categorySuggestion = CategoryIds.OTHER,
            occurredAt = transaction.occurredAt,
            detectedAt = input.notification.timestamp,
            sourceType = ExpenseSource.NOTIFICATION,
            sourceApp = input.notification.applicationName,
            parserId = parser?.parserId ?: "ai-extraction",
            // Este campo sigue representando confianza del parser, nunca el score del modelo.
            confidence = parser?.confidence ?: 0.0,
        )
        publishCandidate(candidate)
    }

    private suspend fun publishCandidate(candidate: ExpenseCandidate) {
        val id = createCandidate(candidate) ?: return
        if (findPendingCandidate(id) == null) return
        runCatching { alertPublisher.publish(candidate.copy(id = id)) }
    }

    private companion object {
        const val RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L
    }
}
