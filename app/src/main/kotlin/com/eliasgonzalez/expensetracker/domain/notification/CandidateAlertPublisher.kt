package com.eliasgonzalez.expensetracker.domain.notification

import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate

/** Publica una alerta del sistema para un candidato pendiente. */
fun interface CandidateAlertPublisher {
    fun publish(candidate: ExpenseCandidate)
}
