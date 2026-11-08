package com.eliasgonzalez.expensetracker.domain.chat

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatPeriodTest {
    private val sunday = LocalDate.of(2026, 11, 8) // domingo: el caso que el modelo resolvio mal en el telefono
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)
    private fun r(p: ChatPeriod, from: LocalDate? = null, to: LocalDate? = null) = p.resolve(sunday, from, to)

    @Test
    fun `esta semana incluye el dia de hoy y empieza el lunes`() {
        assertEquals(d(11, 2) to d(11, 8), r(ChatPeriod.THIS_WEEK))
        assertEquals(d(11, 2) to d(11, 8), ChatPeriod.THIS_WEEK.resolve(d(11, 2), null, null)) // el lunes mismo
        assertEquals(d(11, 2) to d(11, 8), ChatPeriod.THIS_WEEK.resolve(d(11, 4), null, null))
    }

    @Test
    fun `la semana pasada es la anterior completa`() = assertEquals(d(10, 26) to d(11, 1), r(ChatPeriod.LAST_WEEK))

    @Test
    fun `hoy, ayer y los ultimos dias`() {
        assertEquals(sunday to sunday, r(ChatPeriod.TODAY))
        assertEquals(d(11, 7) to d(11, 7), r(ChatPeriod.YESTERDAY))
        assertEquals(d(11, 2) to sunday, r(ChatPeriod.LAST_7_DAYS))
        assertEquals(d(10, 10) to sunday, r(ChatPeriod.LAST_30_DAYS))
    }

    @Test
    fun `este mes y el mes pasado`() {
        assertEquals(d(11, 1) to d(11, 30), r(ChatPeriod.THIS_MONTH))
        assertEquals(d(10, 1) to d(10, 31), r(ChatPeriod.LAST_MONTH))
        assertEquals(LocalDate.of(2025, 12, 1) to LocalDate.of(2025, 12, 31), ChatPeriod.LAST_MONTH.resolve(LocalDate.of(2026, 1, 15), null, null))
    }

    @Test
    fun `sin limite de fechas y fechas propias solo con CUSTOM`() {
        assertEquals(null to null, r(ChatPeriod.ALL, d(11, 1), d(11, 7)))
        assertEquals(d(11, 1) to d(11, 7), r(ChatPeriod.CUSTOM, d(11, 1), d(11, 7)))
        assertEquals(sunday to sunday, r(ChatPeriod.TODAY, d(1, 1), d(1, 2)))
    }
}
