package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisDecision
import com.eliasgonzalez.expensetracker.domain.analysis.DetectedMovement
import com.eliasgonzalez.expensetracker.domain.analysis.NotificationSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.ReconciledTransaction
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository
import kotlinx.coroutines.sync.Mutex
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Registra directamente un resultado AUTO_REGISTER, sin crear un candidato intermedio.
 *
 * Comparte el lock de candidatos con CreateCandidate/ConfirmCandidate para que una
 * notificacion que llega por dos canales no pueda convertirse simultaneamente en un
 * candidato pendiente y en un gasto confirmado. El RegisterExpense interno comparte
 * a su vez el lock de gastos con los flujos manuales y de confirmacion.
 */
class RegisterAnalyzedExpense(
    private val candidates: CandidateRepository,
    private val expenses: ExpenseRepository,
    private val registerExpense: RegisterExpense,
    private val mutex: Mutex = Mutex(),
    private val awaitReady: suspend () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(
        notification: NotificationSnapshot,
        transaction: ReconciledTransaction,
    ): Long? {
        if (transaction.decision != AnalysisDecision.AUTO_REGISTER) return null
        if (transaction.movement !in setOf(DetectedMovement.EXPENSE, DetectedMovement.OUTGOING_TRANSFER)) return null
        val amount = transaction.amount?.takeIf { it > 0 } ?: return null
        val currency = transaction.currency?.takeIf { it.matches(Regex("[A-Z]{3}")) } ?: return null
        val merchant = transaction.merchant?.takeIf(String::isNotBlank) ?: return null
        if (!awaitReady()) return null

        val sourceReference = stableSourceReference(notification)
        return mutex.withCommitLock {
            // Reintento del mismo evento: no vuelve a insertar aunque la llamada anterior
            // haya registrado el gasto y haya fallado antes de marcar el trabajo completado.
            if (expenses.expenses.value.any { it.sourceReference == sourceReference }) {
                return@withCommitLock null
            }
            if (matchesRecentCandidate(amount, currency, merchant, notification)) {
                return@withCommitLock null
            }
            if (matchesRecentExpense(amount, currency, merchant, notification.timestamp)) {
                return@withCommitLock null
            }

            registerExpense(
                Expense(
                    amount = amount,
                    currency = currency,
                    merchant = merchant,
                    occurredAt = transaction.occurredAt,
                    createdAt = now(),
                    source = ExpenseSource.NOTIFICATION,
                    // Los ids negativos se reservan para referencias de auto-registros IA;
                    // los ids positivos actuales identifican candidatos confirmados.
                    sourceReference = sourceReference,
                )
            )
        }
    }

    private fun matchesRecentCandidate(
        amount: Long,
        currency: String,
        merchant: String,
        notification: NotificationSnapshot,
    ): Boolean = candidates.candidates.value.any { existing ->
        if (existing.sourceType == ExpenseSource.NOTIFICATION &&
            existing.detectedAt == notification.timestamp &&
            existing.sourceApp == notification.applicationName
        ) return@any true

        val window = if (existing.status == CandidateStatus.PENDING) {
            DEDUP_WINDOW_MILLIS
        } else {
            DEDUP_RESOLVED_WINDOW_MILLIS
        }
        existing.amount == amount &&
            existing.currency == currency &&
            abs(existing.detectedAt - notification.timestamp) <= window &&
            (if (existing.merchantConfident) {
                existing.merchant.normalizedForMatch() == merchant.normalizedForMatch()
            } else {
                true
            })
    }

    private fun matchesRecentExpense(
        amount: Long,
        currency: String,
        merchant: String,
        occurredAt: Long,
    ): Boolean = expenses.expenses.value.any { existing ->
        existing.amount == amount &&
            existing.currency == currency &&
            abs(existing.createdAt - occurredAt) <= DEDUP_RESOLVED_WINDOW_MILLIS &&
            existing.merchant.normalizedForMatch() == merchant.normalizedForMatch()
    }

    private fun String.normalizedForMatch(): String =
        trim().uppercase().filter(Char::isLetterOrDigit)

    private fun stableSourceReference(notification: NotificationSnapshot): Long {
        val raw = listOf(
            notification.packageName,
            notification.timestamp.toString(),
            notification.title,
            notification.text,
            notification.bigText,
        ).joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        val value = ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).getLong()
        return when (value) {
            Long.MIN_VALUE -> Long.MIN_VALUE
            0L -> -1L
            else -> -abs(value)
        }
    }
}
