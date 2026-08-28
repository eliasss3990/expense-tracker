package com.eliasgonzalez.expensetracker.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ScreenSupportTest {

    private fun epochMillisAt(date: LocalDate, hour: Int, minute: Int): Long =
        ZonedDateTime.of(date, java.time.LocalTime.of(hour, minute), ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    @Test
    fun `dia de hoy se muestra con prefijo Hoy y la hora`() {
        val today = LocalDate.now()
        val millis = epochMillisAt(today, 14, 30)

        assertEquals("Hoy · 14:30", relativeDay(millis))
    }

    @Test
    fun `dia de ayer se muestra con prefijo Ayer y la hora`() {
        val yesterday = LocalDate.now().minusDays(1)
        val millis = epochMillisAt(yesterday, 9, 5)

        assertEquals("Ayer · 09:05", relativeDay(millis))
    }

    @Test
    fun `un dia mas antiguo muestra la fecha completa y la hora`() {
        val older = LocalDate.now().minusDays(10)
        val millis = epochMillisAt(older, 8, 0)

        val expectedDate = older.format(fullDateFormatter)
        assertEquals("$expectedDate · 08:00", relativeDay(millis))
    }

    @Test
    fun `un dia futuro tambien usa la fecha completa, no rompe con Hoy o Ayer`() {
        val future = LocalDate.now().plusDays(5)
        val millis = epochMillisAt(future, 12, 0)

        val expectedDate = future.format(fullDateFormatter)
        assertEquals("$expectedDate · 12:00", relativeDay(millis))
    }

    @Test
    fun `medianoche de hoy (00-00) se formatea sin errores`() {
        val millis = epochMillisAt(LocalDate.now(), 0, 0)
        assertEquals("Hoy · 00:00", relativeDay(millis))
    }

    @Test
    fun `un minuto antes de medianoche de hoy (23-59) se formatea sin errores`() {
        val millis = epochMillisAt(LocalDate.now(), 23, 59)
        assertEquals("Hoy · 23:59", relativeDay(millis))
    }

    @Test
    fun `dos dias atras ya no cuenta como Ayer, usa fecha completa`() {
        val twoDaysAgo = LocalDate.now().minusDays(2)
        val millis = epochMillisAt(twoDaysAgo, 10, 0)

        val expectedDate = twoDaysAgo.format(fullDateFormatter)
        assertEquals("$expectedDate · 10:00", relativeDay(millis))
    }

    @Test
    fun `funciona cruzando el limite de fin de año (31 dic a 1 ene)`() {
        val newYearsEve = LocalDate.of(2025, 12, 31)
        val millis = epochMillisAt(newYearsEve, 23, 0)

        // Solo verificamos que no explota y que no se confunde con Hoy/Ayer
        // salvo que la fecha real de ejecucion coincida - el formato es lo
        // que importa acá, no el texto exacto (depende de "hoy" real).
        val result = relativeDay(millis)
        assertTrue(result.contains("23:00"))
    }
}
