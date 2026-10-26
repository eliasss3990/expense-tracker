package com.eliasgonzalez.expensetracker.domain.catalog

import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository

/**
 * Puerto: responde si una entrada del catalogo esta referenciada por
 * datos existentes. Decide si se puede ELIMINAR (sin referencias) o solo
 * DESACTIVAR (con referencias). Cada tipo de catalogo aporta su criterio
 * sin que el caso de uso conozca los demas modelos.
 */
fun interface CatalogUsage {
    fun isInUse(kind: String, id: String): Boolean
}

/**
 * Una categoria esta en uso si algun gasto la tiene, o si algun candidato
 * PENDIENTE la sugiere: borrarla dejaria a ese candidato apuntando a un id
 * que ya no existe, y al confirmarlo cambiaria de categoria. Los
 * candidatos ya resueltos no cuentan (su dato vive en el gasto resultante).
 */
class ExpenseCatalogUsage(
    private val expenses: ExpenseRepository,
    private val candidates: CandidateRepository,
) : CatalogUsage {
    override fun isInUse(kind: String, id: String): Boolean = when (kind) {
        CatalogKind.CATEGORY ->
            expenses.expenses.value.any { it.categoryId == id } ||
                candidates.candidates.value.any {
                    it.status == CandidateStatus.PENDING && it.categorySuggestion == id
                }
        else -> false
    }
}
