package com.eliasgonzalez.expensetracker.model

enum class CandidateStatus { PENDING, ACCEPTED, EDITED, REJECTED }

enum class Source { MANUAL, QUICK_TILE, NOTIFICATION }

data class ExpenseCandidate(
    val id: Long,
    var amount: Long,
    val currency: String = "PYG",
    var merchant: String,
    var category: String = "OTHER",
    val source: Source,
    val sourceApp: String? = null,
    val detectedAt: Long,
    var status: CandidateStatus = CandidateStatus.PENDING,
)
