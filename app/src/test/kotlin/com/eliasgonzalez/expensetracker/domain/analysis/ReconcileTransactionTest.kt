package com.eliasgonzalez.expensetracker.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcileTransactionTest {

    private val notification = NotificationSnapshot(
        packageName = "com.google.android.gm",
        applicationName = "Gmail",
        title = "Compra aprobada",
        text = "Pagaste Gs. 85.000 en Supermercado Real",
        bigText = "Compra aprobada por Gs. 85.000 en Supermercado Real",
        timestamp = 1_700_000_000_000,
    )
    private val parser = ParserSnapshot(
        amount = 85_000,
        currency = "PYG",
        merchant = "Supermercado Real",
        merchantConfident = true,
        parserId = "test-parser",
    )

    private fun result(
        movement: DetectedMovement = DetectedMovement.EXPENSE,
        amount: Long? = 85_000,
        currency: String? = "PYG",
        merchant: String? = "Supermercado Real",
        movementEvidence: String? = "Compra aprobada",
        amountEvidence: String? = "Gs. 85.000",
        merchantEvidence: String? = "Supermercado Real",
        currencyEvidence: String? = "Gs.",
    ) = TransactionAnalysisResult(
        extraction = TransactionExtraction(
            movement = movement,
            amount = amount,
            currency = currency,
            merchant = merchant,
            movementEvidence = movementEvidence,
            amountEvidence = amountEvidence,
            merchantEvidence = merchantEvidence,
            currencyEvidence = currencyEvidence,
        ),
        modelVersion = "fake-model",
    )

    @Test
    fun `auto registra solo cuando IA y parser concuerdan con evidencia literal`() {
        val reconciled = ReconcileTransaction()(
            TransactionAnalysisInput(notification, parser),
            result(),
        )

        assertEquals(AnalysisDecision.AUTO_REGISTER, reconciled.decision)
        assertEquals(85_000L, reconciled.amount)
        assertTrue(reconciled.reasons.isEmpty())
    }

    @Test
    fun `una notificacion entrante no se auto registra aunque el modelo la llame gasto`() {
        val incoming = notification.copy(
            title = "Transferencia recibida",
            text = "Recibiste Gs. 250.000 en tu cuenta",
        )
        val reconciled = ReconcileTransaction()(
            TransactionAnalysisInput(incoming, null),
            result(
                movement = DetectedMovement.EXPENSE,
                movementEvidence = "Recibiste",
            ),
        )

        assertEquals(AnalysisDecision.REJECT, reconciled.decision)
        assertTrue("INCOMING_MONEY_EVIDENCE" in reconciled.reasons)
    }

    @Test
    fun `sin parser permite auto registro solo con evidencia literal completa`() {
        val reconciled = ReconcileTransaction()(
            TransactionAnalysisInput(notification, null),
            result(),
        )

        assertEquals(AnalysisDecision.AUTO_REGISTER, reconciled.decision)
        assertEquals(85_000L, reconciled.amount)
        assertTrue(reconciled.reasons.isEmpty())
    }

    @Test
    fun `una compra rechazada nunca se auto registra aunque el parser coincida`() {
        val rejectedNotification = notification.copy(
            title = "Compra rechazada",
            text = "Tu compra fue rechazada por Gs. 85.000 en Supermercado Real",
        )
        val reconciled = ReconcileTransaction()(
            TransactionAnalysisInput(rejectedNotification, parser),
            result(movementEvidence = "Compra rechazada"),
        )

        assertEquals(AnalysisDecision.REQUIRES_REVIEW, reconciled.decision)
        assertTrue("TRANSACTION_NOT_APPROVED" in reconciled.reasons)
    }

    @Test
    fun `un conflicto de monto obliga a revision`() {
        val reconciled = ReconcileTransaction()(
            TransactionAnalysisInput(notification, parser),
            result(amount = 80_000),
        )

        assertEquals(AnalysisDecision.REQUIRES_REVIEW, reconciled.decision)
        assertTrue("AMOUNT_CONFLICT" in reconciled.reasons)
    }

    @Test
    fun `sin parser una extraccion plausible no se auto registra`() {
        val reconciled = ReconcileTransaction()(
            TransactionAnalysisInput(notification, null),
            result(currencyEvidence = null),
        )

        assertEquals(AnalysisDecision.REQUIRES_REVIEW, reconciled.decision)
        assertTrue("CURRENCY_EVIDENCE_MISSING" in reconciled.reasons)
    }

    @Test
    fun `un ingreso con evidencia literal se rechaza como gasto`() {
        val incomeNotification = notification.copy(
            title = "Recibiste una transferencia",
            text = "Recibiste Gs. 85.000",
            bigText = "Recibiste Gs. 85.000 de Juan",
        )
        val reconciled = ReconcileTransaction()(
            TransactionAnalysisInput(incomeNotification, null),
            result(
                movement = DetectedMovement.INCOME,
                movementEvidence = "Recibiste",
                amountEvidence = "Gs. 85.000",
            ),
        )

        assertEquals(AnalysisDecision.REJECT, reconciled.decision)
        assertEquals(setOf("NON_EXPENSE_WITH_SOURCE_EVIDENCE"), reconciled.reasons)
    }

    @Test
    fun `el confidence del modelo no forma parte de la decision`() {
        val reconciled = ReconcileTransaction()(
            TransactionAnalysisInput(notification, parser),
            result(),
        )

        assertEquals(AnalysisDecision.AUTO_REGISTER, reconciled.decision)
    }

    @Test
    fun `un movimiento contrario al parser exige revision en vez de rechazo`() {
        val incomeNotification = notification.copy(
            title = "Recibiste una transferencia",
            text = "Recibiste Gs. 85.000",
            bigText = "Recibiste Gs. 85.000 de Juan",
        )
        val reconciled = ReconcileTransaction()(
            TransactionAnalysisInput(incomeNotification, parser),
            result(
                movement = DetectedMovement.INCOME,
                movementEvidence = "Recibiste",
                amountEvidence = "Gs. 85.000",
            ),
        )

        assertEquals(AnalysisDecision.REQUIRES_REVIEW, reconciled.decision)
        assertTrue("MOVEMENT_CONFLICT" in reconciled.reasons)
    }

    @Test
    fun `evidencia inventada obliga a revision`() {
        val reconciled = ReconcileTransaction()(
            TransactionAnalysisInput(notification, parser),
            result(amountEvidence = "Gs. 999.999"),
        )

        assertEquals(AnalysisDecision.REQUIRES_REVIEW, reconciled.decision)
        assertTrue("AMOUNT_EVIDENCE_MISSING" in reconciled.reasons)
    }
}
