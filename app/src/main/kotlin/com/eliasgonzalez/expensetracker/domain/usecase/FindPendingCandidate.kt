package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.repository.CandidateRepository

/**
 * Busca un candidato por id solo si sigue pendiente - un candidato ya
 * aceptado/rechazado no debería poder reabrirse para editar (ej. si el
 * usuario vuelve a tocar una notificación vieja del sistema).
 */
class FindPendingCandidate(private val candidates: CandidateRepository) {
    operator fun invoke(candidateId: Long): ExpenseCandidate? =
        candidates.findById(candidateId)?.takeIf { it.status == CandidateStatus.PENDING }
}
