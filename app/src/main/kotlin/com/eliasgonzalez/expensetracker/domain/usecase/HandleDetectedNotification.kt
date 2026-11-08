package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisEvent
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisMode
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisModeProvider
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisQueueRepository
import com.eliasgonzalez.expensetracker.domain.analysis.CandidateAnnouncer
import com.eliasgonzalez.expensetracker.domain.analysis.ModelAvailability
import com.eliasgonzalez.expensetracker.domain.analysis.ParserEvidence
import com.eliasgonzalez.expensetracker.domain.log.AppLog
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import kotlinx.coroutines.CancellationException

/**
 * Puerta de entrada de un aviso financiero ya filtrado por la allowlist. Decide si el flujo
 * visible es el de siempre (parser -> candidato) o si lo resuelve el analisis con el modelo.
 *
 * - Sin modelo listo, o en modo Sombra: el flujo de siempre; en Sombra ademas se encola el
 *   aviso para que el modelo mida y audite sin cambiar nada de lo que ve el usuario.
 * - Asistido / Automatico con modelo listo: solo se encola; [ProcessAnalysisQueue] decide.
 */
class HandleDetectedNotification(
    private val queue: AnalysisQueueRepository,
    private val createCandidate: CreateCandidate,
    private val findPendingCandidate: FindPendingCandidate,
    private val announcer: CandidateAnnouncer,
    private val modeProvider: AnalysisModeProvider,
    private val modelAvailability: ModelAvailability,
    private val onQueued: () -> Unit,
) {
    suspend operator fun invoke(event: AnalysisEvent) {
        val modelReady = modelAvailability.isReady()
        val mode = modeProvider.currentMode()
        val legacyFlow = !modelReady || mode == AnalysisMode.SHADOW
        if (legacyFlow) createLegacyCandidate(event)
        if (modelReady && queue.enqueue(event) != null) onQueued()
    }

    private suspend fun createLegacyCandidate(event: AnalysisEvent) {
        val parser = (event.parser as? ParserEvidence.Parsed)?.snapshot ?: return
        val candidate = ExpenseCandidate(
            amount = parser.amount,
            currency = parser.currency,
            merchant = parser.merchant,
            merchantConfident = parser.merchantConfident,
            occurredAt = event.notification.timestamp,
            detectedAt = event.notification.timestamp,
            sourceType = ExpenseSource.NOTIFICATION,
            sourceApp = event.notification.applicationName,
            parserId = parser.parserId,
            confidence = parser.confidence,
        )
        val id = createCandidate(candidate) ?: return
        // Si el usuario ya lo resolvio entre el guardado y este punto, la alerta quedaria obsoleta.
        if (findPendingCandidate(id) == null) return
        try {
            announcer.announce(candidate.copy(id = id))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.w("HandleNotification", "No se pudo publicar el aviso del candidato $id", error)
        }
    }
}
