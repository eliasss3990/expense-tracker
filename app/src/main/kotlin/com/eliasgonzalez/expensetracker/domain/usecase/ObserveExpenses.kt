package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Punto único de lectura reactiva de gastos - existe para que la UI no
 * necesite una referencia directa a [ExpenseRepository] (que además
 * expone `save`/`update`/`delete`, mutaciones que la UI no debería poder
 * invocar salteándose los casos de uso correspondientes).
 */
class ObserveExpenses(private val expenses: ExpenseRepository) {
    operator fun invoke(): StateFlow<List<Expense>> = expenses.expenses
}
