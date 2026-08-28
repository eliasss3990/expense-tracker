package com.eliasgonzalez.expensetracker.ui

import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private fun expenseOn(date: LocalDate): Expense {
    val epochMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return Expense(
        amount = 1_000,
        merchant = "Test",
        categoryId = "other",
        occurredAt = epochMillis,
        createdAt = epochMillis,
        source = ExpenseSource.MANUAL,
    )
}

class ExpenseFiltersTest {

    @Test
    fun `ALL acepta cualquier fecha`() {
        assertTrue(matchesDateRange(expenseOn(LocalDate.now().minusYears(5)), DateRangeFilter.ALL))
    }

    @Test
    fun `TODAY solo acepta el dia de hoy`() {
        assertTrue(matchesDateRange(expenseOn(LocalDate.now()), DateRangeFilter.TODAY))
        assertFalse(matchesDateRange(expenseOn(LocalDate.now().minusDays(1)), DateRangeFilter.TODAY))
    }

    @Test
    fun `LAST_7_DAYS incluye el limite pero no un dia mas viejo`() {
        assertTrue(matchesDateRange(expenseOn(LocalDate.now().minusDays(6)), DateRangeFilter.LAST_7_DAYS))
        assertFalse(matchesDateRange(expenseOn(LocalDate.now().minusDays(7)), DateRangeFilter.LAST_7_DAYS))
    }

    @Test
    fun `THIS_MONTH excluye meses anteriores`() {
        assertTrue(matchesDateRange(expenseOn(LocalDate.now()), DateRangeFilter.THIS_MONTH))
        assertFalse(matchesDateRange(expenseOn(LocalDate.now().minusMonths(1)), DateRangeFilter.THIS_MONTH))
    }

    @Test
    fun `SPECIFIC_MONTH solo acepta el mes elegido`() {
        val threeMonthsAgo = LocalDate.now().minusMonths(3)
        val expense = expenseOn(threeMonthsAgo)
        assertTrue(matchesDateRange(expense, DateRangeFilter.SPECIFIC_MONTH, YearMonth.from(threeMonthsAgo)))
        assertFalse(matchesDateRange(expense, DateRangeFilter.SPECIFIC_MONTH, YearMonth.now()))
    }

    @Test
    fun `SPECIFIC_MONTH sin mes elegido no acepta nada`() {
        assertFalse(matchesDateRange(expenseOn(LocalDate.now()), DateRangeFilter.SPECIFIC_MONTH, null))
    }

    @Test
    fun `availableMonths deduplica y ordena de mas reciente a mas viejo`() {
        val expenses = listOf(
            expenseOn(LocalDate.now()),
            expenseOn(LocalDate.now().minusMonths(2)),
            expenseOn(LocalDate.now()),
            expenseOn(LocalDate.now().minusMonths(1)),
        )
        val months = availableMonths(expenses)
        assertEquals(
            listOf(YearMonth.now(), YearMonth.now().minusMonths(1), YearMonth.now().minusMonths(2)),
            months,
        )
    }
}
