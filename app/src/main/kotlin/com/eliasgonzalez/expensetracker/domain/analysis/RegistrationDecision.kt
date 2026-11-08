package com.eliasgonzalez.expensetracker.domain.analysis

enum class RegistrationOutcome { AUTO_REGISTER, REVIEW, DISCARD }

/** Por que se decidio asi. Son codigos estables: se persisten y se loguean para auditar. */
enum class DecisionReason {
    // --- descarte
    PARSER_IGNORED,
    NOT_A_SPEND,
    NOT_COMPLETED_TERMINAL,
    // --- revision
    PARSER_CONFLICT_MOVEMENT,
    AMBIGUOUS_DIRECTION,
    NOT_COMPLETED,
    MODEL_DOUBT,
    NO_SINGLE_OPERATION_AMOUNT,
    INVALID_AMOUNT,
    AMOUNT_NOT_IN_TEXT,
    CURRENCY_MISSING,
    CURRENCY_NOT_AUTO,
    CURRENCY_NOT_IN_TEXT,
    MERCHANT_MISSING,
    MERCHANT_NOT_IN_TEXT,
    PASSES_DISAGREE,
    NO_CROSS_CHECK,
    PARSER_AMOUNT_MISMATCH,
    PARSER_CURRENCY_MISMATCH,
    PARSER_MERCHANT_MISMATCH,
    UNTRUSTED_SOURCE,
}

data class RegistrationDecision(
    val outcome: RegistrationOutcome,
    val reasons: Set<DecisionReason>,
    val amount: Long?,
    val currency: String?,
    val merchant: String?,
    val merchantConfident: Boolean,
)
