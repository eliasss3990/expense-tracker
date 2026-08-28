package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Punto único de lectura reactiva de candidatos - misma razón que
 * [ObserveExpenses]: evita que la UI tenga una referencia directa al
 * repositorio y pueda mutar por fuera de los casos de uso existentes.
 */
class ObserveCandidates(private val candidates: CandidateRepository) {
    operator fun invoke(): StateFlow<List<ExpenseCandidate>> = candidates.candidates
}
