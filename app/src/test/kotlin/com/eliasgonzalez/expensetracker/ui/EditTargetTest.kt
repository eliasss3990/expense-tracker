package com.eliasgonzalez.expensetracker.ui

import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import org.junit.Assert.assertEquals
import org.junit.Test

class EditTargetTest {

    private val candidate = ExpenseCandidate(
        id = 7, amount = 5_000, merchant = "Shell", occurredAt = 1, detectedAt = 1,
        sourceType = ExpenseSource.NOTIFICATION,
    )

    @Test
    fun `sin id a editar es un registro nuevo, sin importar la carga`() {
        assertEquals(EditTarget.NewExpense, resolveEditTarget(null, dataLoaded = true, pendingCandidate = null))
        assertEquals(EditTarget.NewExpense, resolveEditTarget(null, dataLoaded = false, pendingCandidate = null))
    }

    @Test
    fun `con id y el candidato pendiente se edita ese candidato`() {
        assertEquals(EditTarget.Edit(candidate), resolveEditTarget(7, dataLoaded = true, pendingCandidate = candidate))
    }

    @Test
    fun `con id pero ya resuelto NO abre un Nuevo gasto vacio (que duplicaria el gasto)`() {
        val target = resolveEditTarget(7, dataLoaded = true, pendingCandidate = null)

        assertEquals(EditTarget.AlreadyResolved, target)
    }

    @Test
    fun `con id y la carga fallida no se adivina - es un fallo, no un candidato inexistente`() {
        // Antes: lista vacia => "no existe" => formulario vacio => gasto duplicado.
        assertEquals(EditTarget.LoadFailed, resolveEditTarget(7, dataLoaded = false, pendingCandidate = null))
        assertEquals(EditTarget.LoadFailed, resolveEditTarget(7, dataLoaded = false, pendingCandidate = candidate))
    }
}
