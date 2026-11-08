package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository
import kotlinx.coroutines.sync.Mutex
import kotlin.math.abs

sealed interface AutoRegisterResult {
    data class Registered(val expenseId: Long) : AutoRegisterResult

    /** Ya habia un gasto o candidato equivalente (otro canal, reintento). */
    data object Duplicate : AutoRegisterResult

    /** La base aun no cargo: NO es terminal, hay que reintentar. */
    data object NotReady : AutoRegisterResult
}

/**
 * Registra directamente un gasto que el modelo y las reglas dieron por seguro, sin pasar por
 * la Bandeja. Es idempotente por [eventKey]: un reintento tras la muerte del proceso no duplica.
 * Comparte el lock de candidatos con CreateCandidate/ConfirmCandidate (un mismo aviso por dos
 * canales no puede ser candidato y gasto a la vez); RegisterExpense toma el de gastos.
 */
class AutoRegisterExpense(
    private val candidates: CandidateRepository,
    private val expenses: ExpenseRepository,
    private val registerExpense: RegisterExpense,
    private val mutex: Mutex = Mutex(),
    private val awaitReady: suspend () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(
        eventKey: String,
        amount: Long,
        currency: String,
        merchant: String,
        merchantConfident: Boolean,
        occurredAt: Long,
        sourceApp: String,
        categoryId: String,
    ): AutoRegisterResult {
        if (!awaitReady()) return AutoRegisterResult.NotReady
        val reference = referenceFor(eventKey)
        return mutex.withCommitLock {
            val duplicate = expenses.expenses.value.any { it.sourceReference == reference } ||
                candidates.candidates.value.any { matches(it, amount, currency, merchant, merchantConfident, occurredAt) } ||
                expenses.expenses.value.any { sameAutoExpense(it, amount, currency, merchant, merchantConfident, occurredAt) }
            if (duplicate) return@withCommitLock AutoRegisterResult.Duplicate
            AutoRegisterResult.Registered(
                registerExpense(
                    Expense(
                        amount = amount,
                        currency = currency,
                        merchant = merchant,
                        categoryId = categoryId,
                        occurredAt = occurredAt,
                        createdAt = now(),
                        source = ExpenseSource.NOTIFICATION,
                        sourceReference = reference,
                    )
                )
            )
        }
    }

    // Misma regla que CreateCandidate (5 min contra pendientes, 1 min contra resueltos).
    private fun matches(
        existing: ExpenseCandidate,
        amount: Long,
        currency: String,
        merchant: String,
        merchantConfident: Boolean,
        occurredAt: Long,
    ): Boolean {
        val window = if (existing.status == CandidateStatus.PENDING) {
            DEDUP_WINDOW_MILLIS
        } else {
            DEDUP_RESOLVED_WINDOW_MILLIS
        }
        return existing.amount == amount &&
            existing.currency == currency &&
            abs(existing.detectedAt - occurredAt) <= window &&
            merchantsMatch(existing.merchant, existing.merchantConfident, merchant, merchantConfident)
    }

    // Solo contra gastos que YA se auto-registraron desde una notificacion (referencia negativa):
    // los cargados a mano no se comparan, por decision del usuario.
    private fun sameAutoExpense(
        existing: Expense,
        amount: Long,
        currency: String,
        merchant: String,
        merchantConfident: Boolean,
        occurredAt: Long,
    ): Boolean = (existing.sourceReference ?: 0L) < 0 &&
        existing.amount == amount &&
        existing.currency == currency &&
        abs(existing.occurredAt - occurredAt) <= DEDUP_RESOLVED_WINDOW_MILLIS &&
        merchantsMatch(existing.merchant, true, merchant, merchantConfident)

    private fun merchantsMatch(a: String, aConfident: Boolean, b: String, bConfident: Boolean): Boolean =
        if (aConfident && bConfident) a.normalized() == b.normalized() else true

    private fun String.normalized() = trim().uppercase().filter(Char::isLetterOrDigit)

    companion object {
        /** Referencia negativa y estable: los ids positivos son de candidatos confirmados. */
        fun referenceFor(eventKey: String): Long {
            val value = eventKey.take(16).toULongOrNull(16)?.toLong() ?: eventKey.hashCode().toLong()
            return when {
                value == Long.MIN_VALUE -> Long.MIN_VALUE
                value == 0L -> -1L
                else -> -abs(value)
            }
        }
    }
}
