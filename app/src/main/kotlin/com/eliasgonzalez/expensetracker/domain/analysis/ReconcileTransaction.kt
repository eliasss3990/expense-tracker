package com.eliasgonzalez.expensetracker.domain.analysis

/**
 * La decision no usa el confidence autorreportado por el LLM.
 *
 * Auto-registro exige evidencia literal y verificable para movimiento, importe,
 * moneda y comercio. Si hay parser, sus campos deben concordar; sin parser, la
 * evidencia textual debe validar todos los campos. La ausencia de reconocimiento
 * nunca equivale por si sola a REJECT.
 */
enum class AnalysisDecision { AUTO_REGISTER, REQUIRES_REVIEW, REJECT }

data class ReconciledTransaction(
    val decision: AnalysisDecision,
    val movement: DetectedMovement,
    val amount: Long?,
    val currency: String?,
    val merchant: String?,
    val occurredAt: Long,
    val reasons: Set<String>,
)

class ReconcileTransaction {

    operator fun invoke(
        input: TransactionAnalysisInput,
        result: TransactionAnalysisResult,
    ): ReconciledTransaction {
        val notificationText = normalize(input.notification.searchableText())
        val extraction = result.extraction
        val parser = input.parserResult
        val reasons = linkedSetOf<String>()

        if (parser == null &&
            extraction.movement in setOf(DetectedMovement.EXPENSE, DetectedMovement.OUTGOING_TRANSFER) &&
            INCOMING_MONEY_REGEX.containsMatchIn(notificationText)
        ) {
            return reconciled(
                AnalysisDecision.REJECT, extraction, null, input.notification.timestamp,
                setOf("INCOMING_MONEY_EVIDENCE"),
            )
        }

        val movementEvidencePresent = extraction.movementEvidence.isGroundedIn(notificationText)
        val expenseMovementEvidencePresent = extraction.movementEvidence?.let {
            EXPENSE_MOVEMENT_REGEX.containsMatchIn(it)
        } == true
        if (extraction.movement == DetectedMovement.INCOME) {
            val incomingEvidencePresent = extraction.movementEvidence.isGroundedIn(notificationText) &&
                extraction.movementEvidence?.let { INCOMING_MONEY_REGEX.containsMatchIn(it) } == true
            if (incomingEvidencePresent) {
                return reconciled(
                    if (parser == null) AnalysisDecision.REJECT else AnalysisDecision.REQUIRES_REVIEW,
                    extraction,
                    null,
                    input.notification.timestamp,
                    setOf(if (parser == null) "NON_EXPENSE_WITH_SOURCE_EVIDENCE" else "MOVEMENT_CONFLICT"),
                )
            }
            reasons += "MOVEMENT_EVIDENCE_MISSING"
        }

        if (extraction.movement == DetectedMovement.NON_TRANSACTION) {
            val nonTransactionEvidencePresent = extraction.movementEvidence.isGroundedIn(notificationText) &&
                extraction.movementEvidence?.let { NON_TRANSACTION_REGEX.containsMatchIn(it) } == true
            if (nonTransactionEvidencePresent) {
                return reconciled(
                    if (parser == null) AnalysisDecision.REJECT else AnalysisDecision.REQUIRES_REVIEW,
                    extraction,
                    null,
                    input.notification.timestamp,
                    setOf(if (parser == null) "NON_EXPENSE_WITH_SOURCE_EVIDENCE" else "MOVEMENT_CONFLICT"),
                )
            }
            reasons += "MOVEMENT_EVIDENCE_MISSING"
        }

        if (extraction.movement !in setOf(DetectedMovement.EXPENSE, DetectedMovement.OUTGOING_TRANSFER)) {
            reasons += "MOVEMENT_UNCERTAIN"
        }
        if (!movementEvidencePresent || !expenseMovementEvidencePresent) reasons += "MOVEMENT_EVIDENCE_MISSING"
        if (NEGATIVE_STATUS_REGEX.containsMatchIn(notificationText)) {
            if (parser == null && movementEvidencePresent) {
                return reconciled(
                    AnalysisDecision.REJECT, extraction, null, input.notification.timestamp,
                    setOf("TRANSACTION_NOT_APPROVED"),
                )
            }
            reasons += "TRANSACTION_NOT_APPROVED"
        }

        val amount = extraction.amount ?: parser?.amount
        val currency = extraction.currency ?: parser?.currency
        val merchant = extraction.merchant?.takeIf(String::isNotBlank) ?: parser?.merchant
        val amountEvidencePresent = extraction.amountEvidence.isGroundedIn(notificationText)
        val amountEvidenceMatches = amountMatchesEvidence(amount, currency, extraction.amountEvidence, parser != null)
        val currencyEvidencePresent = extraction.currencyEvidence.isGroundedIn(notificationText)
        val currencyEvidenceMatches = currencyEvidenceMatches(currency, extraction.currencyEvidence)
        val merchantEvidencePresent = extraction.merchantEvidence.isGroundedIn(notificationText) &&
            !extraction.merchant.isNullOrBlank() &&
            normalize(extraction.merchant) in normalize(extraction.merchantEvidence.orEmpty())

        if (amount == null || amount <= 0) reasons += "INVALID_AMOUNT"
        if (currency.isNullOrBlank() || !currency.matches(Regex("[A-Z]{3}"))) reasons += "INVALID_CURRENCY"
        if (merchant.isNullOrBlank()) reasons += "MERCHANT_MISSING"
        if (!amountEvidencePresent) reasons += "AMOUNT_EVIDENCE_MISSING"
        if (amountEvidencePresent && !amountEvidenceMatches) reasons += "AMOUNT_EVIDENCE_MISMATCH"
        if (parser == null && (!currencyEvidencePresent || !currencyEvidenceMatches)) {
            reasons += "CURRENCY_EVIDENCE_MISSING"
        }

        if (parser != null) {
            if (extraction.amount != null && extraction.amount != parser.amount) reasons += "AMOUNT_CONFLICT"
            if (extraction.currency != null && !extraction.currency.equals(parser.currency, ignoreCase = true)) {
                reasons += "CURRENCY_CONFLICT"
            }
            if (parser.merchantConfident && extraction.merchant != null &&
                normalize(parser.merchant) != normalize(extraction.merchant)
            ) {
                reasons += "MERCHANT_CONFLICT"
            }
        }

        val merchantTrusted = parser?.merchantConfident == true || merchantEvidencePresent
        if (!merchantTrusted) reasons += "MERCHANT_EVIDENCE_MISSING"

        val decision = if (reasons.isEmpty()) {
            AnalysisDecision.AUTO_REGISTER
        } else {
            AnalysisDecision.REQUIRES_REVIEW
        }

        return ReconciledTransaction(
            decision = decision,
            movement = extraction.movement,
            amount = amount,
            currency = currency?.uppercase(),
            merchant = merchant,
            occurredAt = input.notification.timestamp,
            reasons = reasons,
        )
    }

    private fun reconciled(
        decision: AnalysisDecision,
        extraction: TransactionExtraction,
        parser: ParserSnapshot?,
        fallbackTimestamp: Long,
        reasons: Set<String>,
    ) = ReconciledTransaction(
        decision = decision,
        movement = extraction.movement,
        amount = extraction.amount ?: parser?.amount,
        currency = extraction.currency ?: parser?.currency,
        merchant = extraction.merchant?.takeIf(String::isNotBlank) ?: parser?.merchant,
        occurredAt = fallbackTimestamp,
        reasons = reasons,
    )

    private fun amountMatchesEvidence(
        amount: Long?,
        currency: String?,
        evidence: String?,
        parserAvailable: Boolean,
    ): Boolean {
        if (amount == null || currency != "PYG" || evidence.isNullOrBlank()) return false
        val amountMatch = PYG_AMOUNT_REGEX.find(evidence)
        val amountToken = amountMatch?.groupValues?.get(1)?.takeIf(String::isNotBlank)
            ?: amountMatch?.groupValues?.get(2)?.takeIf(String::isNotBlank)
            ?: (if (parserAvailable) GENERIC_AMOUNT_REGEX.find(evidence)?.value else null)
            ?: return false
        return amountToken.filterNot(Char::isWhitespace).replace(".", "").replace(",", "").toLongOrNull() == amount
    }

    private fun currencyEvidenceMatches(currency: String?, evidence: String?): Boolean {
        if (currency.isNullOrBlank() || evidence.isNullOrBlank()) return false
        return when (currency.uppercase()) {
            "PYG" -> PYG_CURRENCY_REGEX.containsMatchIn(evidence)
            else -> Regex("\\b${Regex.escape(currency)}\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(evidence)
        }
    }

    private fun String?.isGroundedIn(normalizedNotification: String): Boolean =
        !this.isNullOrBlank() && normalize(this) in normalizedNotification

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("\\s+"), " ").trim()

    private companion object {
        val PYG_AMOUNT_REGEX = Regex("""(?:Gs\.?|₲|PYG)\s*([\d][\d., ]*)|([\d][\d., ]*)\s*(?:Gs\.?|₲|PYG)""", RegexOption.IGNORE_CASE)
        val GENERIC_AMOUNT_REGEX = Regex("""[\d][\d., ]*""")
        val PYG_CURRENCY_REGEX = Regex("""(?:Gs\.?|₲|PYG)""", RegexOption.IGNORE_CASE)
        val EXPENSE_MOVEMENT_REGEX = Regex(
            """\b(compra|compraste|pagaste|pago|pag[oó]|enviaste|transferencia\s+(?:a|enviada|realizada)|retiro|consumo|cobro|debito|débito|debitado|cargado|purchase|payment|paid|sent|charged|debit|withdrawal)\b""",
            RegexOption.IGNORE_CASE,
        )
        val NON_TRANSACTION_REGEX = Regex(
            """\b(otp|one[- ]time password|security code|verification code|codigo de seguridad|código de seguridad|codigo de verificacion|código de verificación|inicio de sesion|inicio de sesión|login|new login|nuevo dispositivo|recordatorio|reminder|oferta|promocion|promoción|account statement|resumen de cuenta)\b""",
            RegexOption.IGNORE_CASE,
        )
        val INCOMING_MONEY_REGEX = Regex(
            """\b(recib(?:iste|ió|ida|ido|ieron)|acreditad[oa]s?|acredit[oó]|transferencia\s+(?:entrante|recibida)|dep[oó]sito|ingreso|received|credited|deposit)\b""",
            RegexOption.IGNORE_CASE,
        )
        val NEGATIVE_STATUS_REGEX = Regex(
            """\b(rechazad[oa]s?|declinad[oa]s?|cancelad[oa]s?|fallid[oa]s?|reversad[oa]s?|anulad[oa]s?|denegad[oa]s?|rejected|declined|cancelled|canceled|failed|reversed|denied|not\s+approved|not\s+authorized)\b""",
            RegexOption.IGNORE_CASE,
        )
    }
}
