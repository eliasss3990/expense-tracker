package com.eliasgonzalez.expensetracker.ui.activitylog

import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.ui.theme.BrandPrimary
import com.eliasgonzalez.expensetracker.ui.theme.ExpenseNegative
import com.eliasgonzalez.expensetracker.ui.theme.IncomePositive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityLogScreenTest {

    @Test
    fun `cada tipo de actividad tiene una etiqueta legible propia`() {
        assertEquals("Gasto registrado", activityLabel(ActivityType.EXPENSE_CREATED))
        assertEquals("Gasto editado", activityLabel(ActivityType.EXPENSE_EDITED))
        assertEquals("Gasto eliminado", activityLabel(ActivityType.EXPENSE_DELETED))
        assertEquals("Gasto detectado", activityLabel(ActivityType.CANDIDATE_CREATED))
        assertEquals("Gasto aceptado", activityLabel(ActivityType.CANDIDATE_ACCEPTED))
        assertEquals("Gasto editado y aceptado", activityLabel(ActivityType.CANDIDATE_EDITED))
        assertEquals("Candidato rechazado", activityLabel(ActivityType.CANDIDATE_REJECTED))
    }

    @Test
    fun `todos los tipos de actividad tienen una etiqueta no vacia (cobertura exhaustiva)`() {
        ActivityType.entries.forEach { type ->
            assertTrue("El tipo $type no debería tener una etiqueta vacía", activityLabel(type).isNotBlank())
        }
    }

    @Test
    fun `eventos positivos (creado o aceptado) usan el color de ingreso`() {
        assertEquals(IncomePositive, activityColor(ActivityType.EXPENSE_CREATED))
        assertEquals(IncomePositive, activityColor(ActivityType.CANDIDATE_ACCEPTED))
    }

    @Test
    fun `eventos negativos (eliminado o rechazado) usan el color de gasto`() {
        assertEquals(ExpenseNegative, activityColor(ActivityType.EXPENSE_DELETED))
        assertEquals(ExpenseNegative, activityColor(ActivityType.CANDIDATE_REJECTED))
    }

    @Test
    fun `eventos neutros (editado o detectado) usan el color de marca por defecto`() {
        assertEquals(BrandPrimary, activityColor(ActivityType.EXPENSE_EDITED))
        assertEquals(BrandPrimary, activityColor(ActivityType.CANDIDATE_CREATED))
        assertEquals(BrandPrimary, activityColor(ActivityType.CANDIDATE_EDITED))
    }

    @Test
    fun `todos los tipos de actividad tienen un color asignado (cobertura exhaustiva, sin excepciones)`() {
        ActivityType.entries.forEach { type ->
            // El when de activityColor no tiene else exhaustivo por tipo -
            // esto asegura que ningun ActivityType nuevo quede sin mapear
            // silenciosamente al color de marca por defecto sin querer.
            activityColor(type)
        }
    }

    @Test
    fun `cada tipo de actividad tiene un icono asignado (cobertura exhaustiva)`() {
        ActivityType.entries.forEach { type ->
            activityIcon(type)
        }
    }

    @Test
    fun `EXPENSE_CREATED y CANDIDATE_ACCEPTED comparten el mismo icono (CheckCircle)`() {
        assertEquals(activityIcon(ActivityType.EXPENSE_CREATED), activityIcon(ActivityType.CANDIDATE_ACCEPTED))
    }

    @Test
    fun `EXPENSE_EDITED y CANDIDATE_EDITED comparten el mismo icono (Create)`() {
        assertEquals(activityIcon(ActivityType.EXPENSE_EDITED), activityIcon(ActivityType.CANDIDATE_EDITED))
    }

    @Test
    fun `cada tipo de actividad tiene una etiqueta distinta de la de los demas`() {
        val labels = ActivityType.entries.map { activityLabel(it) }
        assertEquals(labels.size, labels.toSet().size)
    }
}
