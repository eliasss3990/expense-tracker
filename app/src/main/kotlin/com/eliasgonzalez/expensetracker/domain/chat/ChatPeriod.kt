package com.eliasgonzalez.expensetracker.domain.chat

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * El periodo de una consulta. El modelo ELIGE uno; las fechas las calcula la app, porque el modelo se equivoca con la
 * aritmetica de fechas (en el telefono resolvio "esta semana" sin incluir el dia de hoy). Solo CUSTOM usa fechas del modelo.
 * Las semanas empiezan el lunes.
 */
enum class ChatPeriod {
    ALL, TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, LAST_7_DAYS, LAST_30_DAYS, CUSTOM;

    /** (desde, hasta) inclusivos; null = sin limite por ese lado. */
    fun resolve(today: LocalDate, customFrom: LocalDate?, customTo: LocalDate?): Pair<LocalDate?, LocalDate?> = when (this) {
        ALL -> null to null
        TODAY -> today to today
        YESTERDAY -> today.minusDays(1).let { it to it }
        THIS_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
        LAST_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1).let { it to it.plusDays(6) }
        THIS_MONTH -> today.withDayOfMonth(1).let { it to it.with(TemporalAdjusters.lastDayOfMonth()) }
        LAST_MONTH -> today.withDayOfMonth(1).minusMonths(1).let { it to it.with(TemporalAdjusters.lastDayOfMonth()) }
        LAST_7_DAYS -> today.minusDays(6) to today
        LAST_30_DAYS -> today.minusDays(29) to today
        CUSTOM -> customFrom to customTo
    }
}
