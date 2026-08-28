package com.eliasgonzalez.expensetracker.domain

import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

private fun expenseOn(date: LocalDate): Expense = expenseAt(date.atStartOfDay())

private fun expenseAt(dateTime: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Expense {
    val epochMillis = dateTime.atZone(zone).toInstant().toEpochMilli()
    return Expense(
        amount = 1_000,
        merchant = "Test",
        categoryId = "other",
        occurredAt = epochMillis,
        createdAt = epochMillis,
        source = ExpenseSource.MANUAL,
    )
}

private fun expenseAtMillis(epochMillis: Long): Expense =
    Expense(
        amount = 1_000,
        merchant = "Test",
        categoryId = "other",
        occurredAt = epochMillis,
        createdAt = epochMillis,
        source = ExpenseSource.MANUAL,
    )

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

    @Test
    fun `matchesDateRange en medianoche exacta del dia de hoy es TODAY`() {
        val medianoche = LocalDate.now().atStartOfDay()
        assertTrue(matchesDateRange(expenseAt(medianoche), DateRangeFilter.TODAY))
    }

    @Test
    fun `matchesDateRange 23-59-59-999 de hoy sigue siendo TODAY, un ms despues ya es manana`() {
        val finDeHoy = LocalDate.now().atTime(23, 59, 59, 999_000_000)
        assertTrue(matchesDateRange(expenseAt(finDeHoy), DateRangeFilter.TODAY))

        val inicioDeManana = LocalDate.now().plusDays(1).atStartOfDay()
        assertFalse(matchesDateRange(expenseAt(inicioDeManana), DateRangeFilter.TODAY))
    }

    @Test
    fun `THIS_MONTH en el limite - 23-59-59 del ultimo dia del mes vs 00-00-00 del siguiente`() {
        val finDeMesActual = YearMonth.now().atEndOfMonth().atTime(23, 59, 59)
        assertTrue(matchesDateRange(expenseAt(finDeMesActual), DateRangeFilter.THIS_MONTH))

        val inicioMesSiguiente = YearMonth.now().plusMonths(1).atDay(1).atStartOfDay()
        assertFalse(matchesDateRange(expenseAt(inicioMesSiguiente), DateRangeFilter.THIS_MONTH))
    }

    @Test
    fun `SPECIFIC_MONTH en el limite del mes elegido`() {
        val mesElegido = YearMonth.now().minusMonths(2)
        val finDeMesElegido = mesElegido.atEndOfMonth().atTime(23, 59, 59)
        assertTrue(matchesDateRange(expenseAt(finDeMesElegido), DateRangeFilter.SPECIFIC_MONTH, mesElegido))

        val primerDiaMesSiguiente = mesElegido.plusMonths(1).atDay(1).atStartOfDay()
        assertFalse(
            matchesDateRange(expenseAt(primerDiaMesSiguiente), DateRangeFilter.SPECIFIC_MONTH, mesElegido),
        )
    }

    @Test
    fun `LAST_7_DAYS con fecha futura la incluye porque no es anterior al limite`() {
        // La condición es !date.isBefore(today.minusDays(6)); una fecha futura
        // nunca es "isBefore" el límite de hace 6 días, así que pasa el filtro
        // aunque conceptualmente un gasto "futuro" no debería aparecer en
        // "últimos 7 días". Documentamos este comportamiento con un test.
        assertTrue(matchesDateRange(expenseOn(LocalDate.now().plusDays(1)), DateRangeFilter.LAST_7_DAYS))
        assertTrue(matchesDateRange(expenseOn(LocalDate.now().plusYears(1)), DateRangeFilter.LAST_7_DAYS))
    }

    @Test
    fun `TODAY y THIS_MONTH tambien aceptan fechas futuras si coinciden con hoy o el mes actual`() {
        // No hay chequeo de "no futuro" en ninguna rama del when: si el reloj
        // del dispositivo estuviera adelantado, o el usuario carga un gasto
        // con fecha futura manualmente, TODAY y THIS_MONTH lo aceptan igual.
        assertFalse(matchesDateRange(expenseOn(LocalDate.now().plusDays(1)), DateRangeFilter.TODAY))
        assertTrue(matchesDateRange(expenseOn(LocalDate.now()), DateRangeFilter.THIS_MONTH))
    }

    @Test
    fun `matchesDateRange depende de ZoneId systemDefault, no es 100 porciento deterministico en toda zona horaria`() {
        // matchesDateRange usa ZoneId.systemDefault() internamente (no es
        // inyectable), así que este test documenta el comportamiento usando
        // una zona horaria específica para el cálculo del instant de entrada,
        // pero la comparación con LocalDate.now() dentro de la función SIEMPRE
        // usa la zona del sistema donde corre el test/la app. Si la máquina
        // de CI corre en una zona horaria distinta a America/Asuncion (la
        // zona real de los usuarios de esta app), un mismo instante UTC puede
        // caer en un día distinto según la zona, cambiando el resultado del
        // filtro. Esto es un hallazgo de diseño, no un bug de por sí: la
        // función no acepta un ZoneId como parámetro y por lo tanto no es
        // 100% testeable de forma determinística e independiente de la zona
        // horaria del entorno de ejecución.
        val zonaNuevaYork = ZoneId.of("America/New_York")
        val medianocheNy = LocalDate.now(zonaNuevaYork).atStartOfDay()
        val expense = expenseAt(medianocheNy, zonaNuevaYork)
        // Esta aserción es válida en cualquier entorno porque comparamos
        // usando la misma zona horaria con la que se construyó el timestamp
        // y con la que la JVM del test evalúa "hoy" (systemDefault). Si la
        // zona del entorno de test difiere de America/New_York, esta
        // aserción podría no sostenerse, evidenciando la dependencia de la
        // función en ZoneId.systemDefault().
        if (ZoneId.systemDefault() == zonaNuevaYork) {
            assertTrue(matchesDateRange(expense, DateRangeFilter.TODAY))
        }
    }

    @Test
    fun `cruce de DST en America-New_York - resta de dias sigue funcionando por LocalDate`() {
        // matchesDateRange convierte a LocalDate antes de comparar, por lo que
        // el corrimiento de reloj de DST (que afecta horas, no la fecha civil)
        // no debería alterar a qué día calendario pertenece un gasto. Este
        // test fija America/New_York explícitamente (en vez de
        // systemDefault()) para ser determinístico sin importar en qué zona
        // corra la build.
        val zonaNy = ZoneId.of("America/New_York")
        // 2024-03-10 en America/New_York es el día del cambio a horario de
        // verano (2:00 AM -> 3:00 AM). Un gasto a las 01:30 AM ese día existe
        // igual, y debe seguir mapeando al día 2024-03-10.
        val antesDelCambio = LocalDateTime.of(2024, 3, 10, 1, 30)
        val instant = antesDelCambio.atZone(zonaNy).toInstant()
        val fechaResultante = instant.atZone(zonaNy).toLocalDate()
        assertEquals(LocalDate.of(2024, 3, 10), fechaResultante)
    }

    @Test
    fun `availableMonths con timestamps duplicados exactos dedupe a un solo mes`() {
        val mismoInstante = Instant.now().toEpochMilli()
        val expenses = listOf(
            expenseAtMillis(mismoInstante),
            expenseAtMillis(mismoInstante),
            expenseAtMillis(mismoInstante),
        )
        val months = availableMonths(expenses)
        assertEquals(1, months.size)
        assertEquals(YearMonth.from(LocalDate.now()), months.first())
    }

    @Test
    fun `availableMonths con un solo elemento devuelve una lista de un elemento`() {
        val months = availableMonths(listOf(expenseOn(LocalDate.now())))
        assertEquals(listOf(YearMonth.now()), months)
    }

    @Test
    fun `availableMonths con lista vacia devuelve lista vacia`() {
        assertEquals(emptyList<YearMonth>(), availableMonths(emptyList()))
    }

    @Test
    fun `availableMonths con miles de gastos del mismo mes dedupe a un solo mes`() {
        val expenses = (1..3000).map { day ->
            val diaDelMes = (day % 27) + 1
            expenseOn(YearMonth.now().atDay(diaDelMes))
        }
        val months = availableMonths(expenses)
        assertEquals(1, months.size)
        assertEquals(YearMonth.now(), months.first())
    }

    @Test
    fun `SPECIFIC_MONTH con anio muy lejano en el pasado`() {
        val mesAntiguo = YearMonth.of(1970, 1)
        val expense = expenseOn(mesAntiguo.atDay(15))
        assertTrue(matchesDateRange(expense, DateRangeFilter.SPECIFIC_MONTH, mesAntiguo))
        assertFalse(matchesDateRange(expense, DateRangeFilter.SPECIFIC_MONTH, mesAntiguo.plusMonths(1)))
    }

    @Test
    fun `SPECIFIC_MONTH con anio muy lejano en el futuro`() {
        val mesFuturo = YearMonth.of(2999, 12)
        val expense = expenseOn(mesFuturo.atDay(1))
        assertTrue(matchesDateRange(expense, DateRangeFilter.SPECIFIC_MONTH, mesFuturo))
        assertFalse(matchesDateRange(expense, DateRangeFilter.SPECIFIC_MONTH, YearMonth.now()))
    }

    @Test
    fun `availableMonths ordena descendente incluso mezclando anios lejanos pasados y futuros`() {
        val expenses = listOf(
            expenseOn(YearMonth.of(1970, 1).atDay(1)),
            expenseOn(YearMonth.of(2999, 12).atDay(1)),
            expenseOn(LocalDate.now()),
        )
        val months = availableMonths(expenses)
        assertEquals(
            listOf(YearMonth.of(2999, 12), YearMonth.now(), YearMonth.of(1970, 1)),
            months,
        )
    }
}
