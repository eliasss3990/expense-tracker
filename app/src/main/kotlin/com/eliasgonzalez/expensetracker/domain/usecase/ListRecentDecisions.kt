package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.analysis.DecisionAudit
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionAuditRepository

/** Las decisiones mas recientes del analisis, para responder "por que este gasto salio asi". */
class ListRecentDecisions(private val audit: DecisionAuditRepository) {
    suspend operator fun invoke(limit: Int = 50): List<DecisionAudit> = audit.recent(limit)
}
