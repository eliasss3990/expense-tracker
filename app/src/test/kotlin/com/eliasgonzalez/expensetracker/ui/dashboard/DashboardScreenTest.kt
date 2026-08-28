package com.eliasgonzalez.expensetracker.ui.dashboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class DashboardScreenTest {

    private fun epochMillisAt(date: LocalDate): Long =
        ZonedDateTime.of(date, LocalTime.NOON, ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `una fecha de hoy esta en el mes actual`() {
        assertTrue(isInCurrentMonth(epochMillisAt(LocalDate.now())))
    }

    @Test
    fun `una fecha del mes pasado no esta en el mes actual`() {
        val lastMonth = YearMonth.now().minusMonths(1).atDay(1)
        assertFalse(isInCurrentMonth(epochMillisAt(lastMonth)))
    }

    @Test
    fun `una fecha del mes que viene no esta en el mes actual`() {
        val nextMonth = YearMonth.now().plusMonths(1).atDay(1)
        assertFalse(isInCurrentMonth(epochMillisAt(nextMonth)))
    }

    @Test
    fun `el primer y ultimo dia del mes actual cuentan como el mes actual`() {
        val firstDay = YearMonth.now().atDay(1)
        val lastDay = YearMonth.now().atEndOfMonth()

        assertTrue(isInCurrentMonth(epochMillisAt(firstDay)))
        assertTrue(isInCurrentMonth(epochMillisAt(lastDay)))
    }

    @Test
    fun `una fecha del mismo mes pero del anio pasado no cuenta (no compara solo el numero de mes)`() {
        val sameMonthLastYear = YearMonth.now().minusYears(1).atDay(1)
        assertFalse(isInCurrentMonth(epochMillisAt(sameMonthLastYear)))
    }

    @Test
    fun `29 de febrero de un anio bisiesto no rompe el calculo`() {
        val leapDay = LocalDate.of(2024, 2, 29)
        // Solo nos interesa que no explote con una fecha límite real; el
        // resultado (true/false) depende de si "ahora" es feb 2024.
        isInCurrentMonth(epochMillisAt(leapDay))
    }

    @Test
    fun `el 31 de diciembre del anio pasado no cuenta como el mes actual (no cruza el limite de anio)`() {
        val lastDayOfPreviousYear = LocalDate.of(YearMonth.now().year - 1, 12, 31)
        assertFalse(isInCurrentMonth(epochMillisAt(lastDayOfPreviousYear)))
    }
}
