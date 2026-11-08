package com.eliasgonzalez.expensetracker.ui.ai

import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisMode
import com.eliasgonzalez.expensetracker.domain.analysis.CategorySource
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionAudit
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionEffect
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionReason
import com.eliasgonzalez.expensetracker.domain.analysis.ModelInstallState
import com.eliasgonzalez.expensetracker.domain.analysis.ModelInstallStatus
import com.eliasgonzalez.expensetracker.domain.analysis.RegistrationOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPresentationTest {

    private fun audit(
        effect: DecisionEffect,
        reasons: Set<DecisionReason> = emptySet(),
        outcome: RegistrationOutcome? = null,
        categorySource: CategorySource? = null,
        errorClass: String? = null,
    ) = DecisionAudit("k", 1L, AnalysisMode.AUTOMATIC, "Banco", outcome, reasons, effect, null, null, emptyList(), errorClass,
        categoryId = categorySource?.let { "FOOD" }, categorySource = categorySource)

    @Test
    fun `cada codigo que se guarda en la auditoria tiene una explicacion en castellano`() {
        // Si se agrega un enum sin explicarlo, la pantalla mostraria un hueco justo donde se pregunta "por que".
        DecisionEffect.entries.forEach { assertTrue(it.name, AiPresentation.effectLabel(it).isNotBlank()) }
        DecisionReason.entries.forEach { assertTrue(it.name, AiPresentation.reasonText(it).isNotBlank()) }
        CategorySource.entries.forEach { assertTrue(it.name, AiPresentation.categorySourceLabel(it).isNotBlank()) }
        AnalysisMode.entries.forEach {
            assertTrue(AiPresentation.modeTitle(it).isNotBlank() && AiPresentation.modeDescription(it).isNotBlank())
        }
        ModelInstallStatus.entries.forEach { assertTrue(it.name, AiPresentation.installLabel(ModelInstallState(it)).isNotBlank()) }
    }

    @Test
    fun `dos motivos distintos nunca se explican igual`() {
        val texts = DecisionReason.entries.map(AiPresentation::reasonText)
        assertEquals(texts.size, texts.toSet().size)
    }

    @Test
    fun `un gasto registrado solo se explica y muestra de donde salio la categoria`() {
        val lines = AiPresentation.explain(audit(DecisionEffect.AUTO_REGISTERED, categorySource = CategorySource.MEMORY), "Comida")
        assertTrue(lines.first().contains("registró solo"))
        assertEquals("Categoría: Comida (de tu historial con ese comercio).", lines.last())
    }

    @Test
    fun `un candidato lista cada motivo`() {
        val reasons = setOf(DecisionReason.NOT_COMPLETED, DecisionReason.PASSES_DISAGREE)
        val lines = AiPresentation.explain(audit(DecisionEffect.CANDIDATE_CREATED, reasons), null)
        reasons.forEach { r -> assertTrue(r.name, lines.any { it.contains(AiPresentation.reasonText(r)) }) }
    }

    @Test
    fun `un descarte explica el motivo y ofrece recuperarlo`() {
        val lines = AiPresentation.explain(audit(DecisionEffect.DISCARDED, setOf(DecisionReason.NOT_A_SPEND)), null)
        assertTrue(lines.any { it.contains(AiPresentation.reasonText(DecisionReason.NOT_A_SPEND)) })
        assertTrue(lines.any { it.contains("Recuperar") })
    }

    @Test
    fun `en sombra se ve lo que el modelo habria hecho`() {
        fun shadow(o: RegistrationOutcome?) = AiPresentation.explain(audit(DecisionEffect.SHADOW_ONLY, outcome = o), null).single()
        assertTrue(shadow(RegistrationOutcome.AUTO_REGISTER).contains("registrado solo"))
        assertTrue(shadow(RegistrationOutcome.REVIEW).contains("candidato"))
        assertTrue(shadow(RegistrationOutcome.DISCARD).contains("descartado"))
        assertTrue(shadow(null).contains("no llegó a decidir"))
    }

    @Test
    fun `un fallo del modelo muestra el tipo de error y no un mensaje con datos`() {
        val line = AiPresentation.explain(audit(DecisionEffect.FALLBACK_LEGACY, errorClass = "IllegalStateException"), null).single()
        assertTrue(line.contains("IllegalStateException"))
    }

    @Test
    fun `sin fuente de categoria no se inventa la linea de categoria`() {
        assertFalse(AiPresentation.explain(audit(DecisionEffect.DISCARDED), "Comida").any { it.startsWith("Categoría") })
    }

    @Test
    fun `el progreso de la descarga se muestra y ocupado es todo lo que esta en marcha`() {
        assertEquals("Descargando 42%", AiPresentation.installLabel(ModelInstallState(ModelInstallStatus.DOWNLOADING, 42)))
        assertEquals("Descargando", AiPresentation.installLabel(ModelInstallState(ModelInstallStatus.DOWNLOADING, null)))
        assertTrue(AiPresentation.isBusy(ModelInstallState(ModelInstallStatus.VERIFYING)))
        assertTrue(AiPresentation.isBusy(ModelInstallState(ModelInstallStatus.PAUSED)))
        assertFalse(AiPresentation.isBusy(ModelInstallState(ModelInstallStatus.INSTALLED)))
        assertFalse(AiPresentation.isBusy(ModelInstallState(ModelInstallStatus.NOT_INSTALLED)))
        assertFalse(AiPresentation.isBusy(ModelInstallState(ModelInstallStatus.FAILED)))
    }
}
