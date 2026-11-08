package com.eliasgonzalez.expensetracker.domain.analysis

/** Cuanto se confia en el CONTENIDO de la fuente (el paquete lo fija el sistema, el texto no). */
enum class SourceTrust {
    /** App del banco/billetera: el contenido lo genera la entidad. */
    BANK,

    /**
     * Buzon (ej. Gmail): cualquiera puede mandar un mail; solo se auto-registra si lo reconocio un parser
     * CURADO para un formato conocido (no el generico, que acepta casi cualquier texto con un monto).
     */
    MAILBOX,

    /** Nunca se auto-registra. */
    UNTRUSTED,
}

data class RegistrationPolicy(
    val trust: SourceTrust,
    val requireCrossCheck: Boolean = true,
    /** Los importes de la app son enteros: solo monedas sin decimales se auto-registran. */
    val autoCurrencies: Set<String> = setOf("PYG"),
)

/**
 * Decide, sin regex de contenido, que hacer con lo que interpreto el modelo.
 * El modelo entiende la semantica (direccion, estado, rol de cada cifra); aca solo se
 * comprueba coherencia: que no invente numeros, que las pasadas y el parser coincidan,
 * que la fuente sea confiable. Ante la duda: REVIEW (candidato), nunca perder el aviso.
 */
class DecideRegistration {

    operator fun invoke(
        notification: NotificationSnapshot,
        parser: ParserEvidence,
        results: List<InterpretationResult>,
        policy: RegistrationPolicy,
    ): RegistrationDecision {
        if (parser is ParserEvidence.ExplicitlyIgnored) {
            return RegistrationDecision(
                RegistrationOutcome.DISCARD, setOf(DecisionReason.PARSER_IGNORED), null, null, null, false,
            )
        }
        val primary = results.firstOrNull { it.pass == InterpretationPass.PRIMARY }?.interpretation
            ?: error("Se necesita la pasada PRIMARY")
        val cross = results.firstOrNull { it.pass == InterpretationPass.CROSS_CHECK }?.interpretation
        val parsed = (parser as? ParserEvidence.Parsed)?.snapshot

        val notASpend = primary.direction in NON_SPENDING_DIRECTIONS ||
            primary.status in TERMINAL_NEGATIVE_STATUSES
        if (notASpend) return decideNonSpend(parsed, primary, cross, policy)

        val reasons = linkedSetOf<DecisionReason>()
        if (primary.direction != MoneyDirection.OUTGOING) reasons += DecisionReason.AMBIGUOUS_DIRECTION
        if (primary.status != OperationStatus.COMPLETED) reasons += DecisionReason.NOT_COMPLETED
        if (primary.doubts.isNotEmpty()) reasons += DecisionReason.MODEL_DOUBT

        val operation = primary.operationAmount
        if (operation == null) reasons += DecisionReason.NO_SINGLE_OPERATION_AMOUNT
        val amount = operation?.value
        if (amount != null) {
            if (amount <= 0) reasons += DecisionReason.INVALID_AMOUNT
            else if (!NumericTokens.contains(notification.searchableText(), amount)) {
                reasons += DecisionReason.AMOUNT_NOT_IN_TEXT
            }
        }

        val currency = (operation?.currency ?: parsed?.currency)?.uppercase()
        when {
            currency.isNullOrBlank() -> reasons += DecisionReason.CURRENCY_MISSING
            currency !in policy.autoCurrencies -> reasons += DecisionReason.CURRENCY_NOT_AUTO
        }

        val merchant = primary.merchant?.takeIf(String::isNotBlank) ?: parsed?.merchant
        when {
            merchant.isNullOrBlank() -> reasons += DecisionReason.MERCHANT_MISSING
            parsed == null && !NumericTokens.mentionsAnyWord(notification.searchableText(), merchant) ->
                reasons += DecisionReason.MERCHANT_NOT_IN_TEXT
        }

        when {
            cross != null && !agree(primary, cross) -> reasons += DecisionReason.PASSES_DISAGREE
            cross == null && policy.requireCrossCheck -> reasons += DecisionReason.NO_CROSS_CHECK
        }

        if (parsed != null) {
            if (amount != null && amount != parsed.amount) reasons += DecisionReason.PARSER_AMOUNT_MISMATCH
            if (currency != null && !currency.equals(parsed.currency, ignoreCase = true)) {
                reasons += DecisionReason.PARSER_CURRENCY_MISMATCH
            }
            if (parsed.merchantConfident && !primary.merchant.isNullOrBlank() &&
                !merchantsAgree(parsed.merchant, primary.merchant)
            ) {
                reasons += DecisionReason.PARSER_MERCHANT_MISMATCH
            }
        }

        val trusted = when (policy.trust) {
            SourceTrust.BANK -> true
            SourceTrust.MAILBOX -> parsed?.curated == true
            SourceTrust.UNTRUSTED -> false
        }
        if (!trusted) reasons += DecisionReason.UNTRUSTED_SOURCE

        // Si hay parser, sus datos (curados) son los que viajan al candidato.
        return RegistrationDecision(
            outcome = if (reasons.isEmpty()) RegistrationOutcome.AUTO_REGISTER else RegistrationOutcome.REVIEW,
            reasons = reasons,
            amount = if (reasons.isEmpty()) amount else parsed?.amount ?: amount,
            currency = currency ?: parsed?.currency,
            merchant = if (parsed?.merchantConfident == true) parsed.merchant else merchant,
            merchantConfident = parsed?.merchantConfident == true,
        )
    }

    /**
     * Descartar es lo unico que "pierde" un aviso, asi que exige mas: el parser curado no debe
     * haber visto un gasto, y las dos pasadas deben coincidir. Si no, a revision.
     */
    private fun decideNonSpend(
        parsed: ParserSnapshot?,
        primary: TransactionInterpretation,
        cross: TransactionInterpretation?,
        policy: RegistrationPolicy,
    ): RegistrationDecision {
        val reasons = linkedSetOf<DecisionReason>()
        when {
            parsed != null -> reasons += DecisionReason.PARSER_CONFLICT_MOVEMENT
            cross != null && !agree(primary, cross) -> reasons += DecisionReason.PASSES_DISAGREE
            cross == null && policy.requireCrossCheck -> reasons += DecisionReason.NO_CROSS_CHECK
        }
        // Las dos pasadas son el mismo modelo: sus errores estan correlacionados. Descartar (lo unico
        // que hace desaparecer un aviso) exige ademas que ninguna haya declarado dudas.
        if (primary.doubts.isNotEmpty() || cross?.doubts?.isNotEmpty() == true) reasons += DecisionReason.MODEL_DOUBT
        if (reasons.isNotEmpty()) {
            return RegistrationDecision(
                RegistrationOutcome.REVIEW, reasons, parsed?.amount, parsed?.currency, parsed?.merchant,
                parsed?.merchantConfident == true,
            )
        }
        val why = if (primary.direction in NON_SPENDING_DIRECTIONS) {
            DecisionReason.NOT_A_SPEND
        } else {
            DecisionReason.NOT_COMPLETED_TERMINAL
        }
        return RegistrationDecision(RegistrationOutcome.DISCARD, setOf(why), null, null, null, false)
    }

    private fun agree(a: TransactionInterpretation, b: TransactionInterpretation): Boolean =
        a.direction == b.direction &&
            a.status == b.status &&
            a.operationAmount?.value == b.operationAmount?.value &&
            a.operationAmount?.currency.equals(b.operationAmount?.currency, ignoreCase = true)

    private fun merchantsAgree(a: String, b: String): Boolean =
        NumericTokens.normalizeForMatch(a) == NumericTokens.normalizeForMatch(b)

    private companion object {
        val NON_SPENDING_DIRECTIONS = setOf(MoneyDirection.INCOMING, MoneyDirection.REFUND, MoneyDirection.NONE)
        val TERMINAL_NEGATIVE_STATUSES = setOf(OperationStatus.DECLINED, OperationStatus.REVERSED)
    }
}
