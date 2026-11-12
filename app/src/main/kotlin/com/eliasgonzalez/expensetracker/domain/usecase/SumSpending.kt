package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.Expense
import java.time.LocalDate
import java.time.ZoneId

/** Moneda en la que la app registra los gastos por defecto (ver [Expense.currency]). */
const val DEFAULT_CURRENCY = "PYG"

data class SpendingTotal(val total: Long, val count: Int, val currency: String)

/**
 * Suma los gastos de [currency] cuya fecha ([Expense.occurredAt]) cae en [from]..[to] (inclusivos, en la zona [zone]);
 * null = sin limite por ese lado. Funcion pura: no toca la base ni Android.
 */
fun sumSpending(
    expenses: List<Expense>,
    from: LocalDate?,
    to: LocalDate?,
    zone: ZoneId,
    currency: String = DEFAULT_CURRENCY,
): SpendingTotal {
    val fromMillis = from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
    val toExclusiveMillis = to?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
    val inRange = expenses.filter {
        it.currency == currency &&
            (fromMillis == null || it.occurredAt >= fromMillis) &&
            (toExclusiveMillis == null || it.occurredAt < toExclusiveMillis)
    }
    return SpendingTotal(total = inRange.sumOf { it.amount }, count = inRange.size, currency = currency)
}
