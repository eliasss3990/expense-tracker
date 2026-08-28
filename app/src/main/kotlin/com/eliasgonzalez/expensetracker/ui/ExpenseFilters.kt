package com.eliasgonzalez.expensetracker.ui

import com.eliasgonzalez.expensetracker.domain.model.Expense
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class DateRangeFilter(val label: String) {
    ALL("Todo"),
    TODAY("Hoy"),
    LAST_7_DAYS("Últimos 7 días"),
    THIS_MONTH("Este mes"),
    SPECIFIC_MONTH("Mes elegido"),
}

fun matchesDateRange(expense: Expense, filter: DateRangeFilter, specificMonth: YearMonth? = null): Boolean {
    if (filter == DateRangeFilter.ALL) return true
    val date = Instant.ofEpochMilli(expense.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (filter) {
        DateRangeFilter.ALL -> true
        DateRangeFilter.TODAY -> date == today
        DateRangeFilter.LAST_7_DAYS -> !date.isBefore(today.minusDays(6))
        DateRangeFilter.THIS_MONTH -> YearMonth.from(date) == YearMonth.now()
        DateRangeFilter.SPECIFIC_MONTH -> specificMonth != null && YearMonth.from(date) == specificMonth
    }
}

/** Meses con al menos un movimiento, más recientes primero - así el
 * selector de "otro mes" solo ofrece meses donde hay algo que ver. */
fun availableMonths(expenses: List<Expense>): List<YearMonth> =
    expenses
        .map { YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault())) }
        .distinct()
        .sortedDescending()
