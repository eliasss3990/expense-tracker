package com.eliasgonzalez.expensetracker.ui

import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate

/**
 * Que tiene que hacer QuickAddActivity segun como llego. Es una funcion pura
 * para poder probar los casos de arranque en frio sin una Activity.
 *
 * Antes la actividad buscaba el candidato leyendo la lista sin esperar a que
 * cargara la base: con el proceso recien arrancado (tocar "Editar" en una
 * notificacion colgada) la lista estaba vacia, el candidato "no existia", se
 * abria un "Nuevo gasto" vacio y al guardar se DUPLICABA el gasto.
 */
sealed interface EditTarget {
    /** Registro rapido normal: no viene a editar nada. */
    data object NewExpense : EditTarget

    data class Edit(val candidate: ExpenseCandidate) : EditTarget

    /** Vino a editar un candidato que ya no esta pendiente (resuelto desde otro lado). */
    data object AlreadyResolved : EditTarget

    /** La base no termino de cargar bien: no se puede saber, no se adivina. */
    data object LoadFailed : EditTarget
}

fun resolveEditTarget(
    editId: Long?,
    dataLoaded: Boolean,
    pendingCandidate: ExpenseCandidate?,
): EditTarget = when {
    editId == null -> EditTarget.NewExpense
    !dataLoaded -> EditTarget.LoadFailed
    pendingCandidate == null -> EditTarget.AlreadyResolved
    else -> EditTarget.Edit(pendingCandidate)
}
