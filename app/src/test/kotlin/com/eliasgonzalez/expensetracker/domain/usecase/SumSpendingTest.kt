package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SumSpendingTest {

    private val zone = ZoneId.of("America/Asuncion")

    private fun expense(day: LocalDate, hour: Int = 12, amount: Long, currency: String = "PYG") = Expense(
        amount = amount,
        currency = currency,
        merchant = "X",
        occurredAt = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        createdAt = 0,
        source = ExpenseSource.MANUAL,
    )

    @Test
    fun `suma solo los gastos dentro del rango inclusivo`() {
        val d = LocalDate.of(2026, 11, 10)
        val expenses = listOf(
            expense(d.minusDays(1), amount = 1_000),
            expense(d, hour = 0, amount = 2_000),
            expense(d, hour = 23, amount = 3_000),
            expense(d.plusDays(1), amount = 4_000),
        )
        val r = sumSpending(expenses, from = d, to = d, zone = zone)
        assertEquals(5_000L, r.total)
        assertEquals(2, r.count)
    }

    @Test
    fun `sin limites suma todo`() {
        val d = LocalDate.of(2026, 11, 10)
        val r = sumSpending(listOf(expense(d, amount = 10), expense(d.minusYears(1), amount = 20)), null, null, zone)
        assertEquals(30L, r.total)
        assertEquals(2, r.count)
    }

    @Test
    fun `ignora gastos de otra moneda`() {
        val d = LocalDate.of(2026, 11, 10)
        val r = sumSpending(listOf(expense(d, amount = 100), expense(d, amount = 999, currency = "USD")), d, d, zone)
        assertEquals(100L, r.total)
        assertEquals(1, r.count)
    }

    @Test
    fun `lista vacia da cero`() {
        val r = sumSpending(emptyList(), null, null, zone)
        assertEquals(0L, r.total)
        assertEquals(0, r.count)
    }
}
