package com.eliasgonzalez.expensetracker.ui.ai

import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisMode
import com.eliasgonzalez.expensetracker.domain.analysis.CategorySource
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionAudit
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionEffect
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionReason
import com.eliasgonzalez.expensetracker.domain.analysis.ModelInstallState
import com.eliasgonzalez.expensetracker.domain.analysis.ModelInstallStatus
import com.eliasgonzalez.expensetracker.domain.analysis.RegistrationOutcome

/**
 * Los textos de la pantalla de IA, en Kotlin puro para poder probarlos: cada codigo que se guarda en la
 * auditoria (efecto, motivo, fuente de la categoria) tiene su explicacion en castellano, y un test falla si se
 * agrega uno sin explicarlo. Es lo que responde "por que este gasto salio asi" sin mirar logs.
 */
internal object AiPresentation {

    fun modeTitle(mode: AnalysisMode) = when (mode) {
        AnalysisMode.SHADOW -> "Sombra"
        AnalysisMode.ASSISTED -> "Asistido"
        AnalysisMode.AUTOMATIC -> "Automático"
    }

    fun modeDescription(mode: AnalysisMode) = when (mode) {
        AnalysisMode.SHADOW -> "Todo sigue como siempre. El modelo analiza en silencio y queda anotado lo que habría hecho, para medirlo sin riesgo."
        AnalysisMode.ASSISTED -> "El modelo propone: cada gasto llega como candidato, ya con su categoría, para que lo aceptes."
        AnalysisMode.AUTOMATIC -> "Registra solo los gastos que el modelo da por seguros. Lo dudoso llega como candidato."
    }

    fun installLabel(state: ModelInstallState): String = when (state.status) {
        ModelInstallStatus.NOT_INSTALLED -> "No instalado"
        ModelInstallStatus.QUEUED -> "En cola para descargar"
        ModelInstallStatus.DOWNLOADING -> "Descargando" + (state.progressPercent?.let { " $it%" } ?: "")
        ModelInstallStatus.PAUSED -> "Descarga en pausa (esperando Wi-Fi)"
        ModelInstallStatus.DOWNLOADED -> "Descargado, verificando"
        ModelInstallStatus.VERIFYING -> "Verificando la integridad del archivo"
        ModelInstallStatus.INSTALLED -> "Instalado y verificado"
        ModelInstallStatus.FAILED -> "La descarga falló"
    }

    fun isBusy(state: ModelInstallState) = state.status in setOf(
        ModelInstallStatus.QUEUED, ModelInstallStatus.DOWNLOADING, ModelInstallStatus.PAUSED,
        ModelInstallStatus.DOWNLOADED, ModelInstallStatus.VERIFYING,
    )

    fun effectLabel(effect: DecisionEffect) = when (effect) {
        DecisionEffect.AUTO_REGISTERED -> "Registrado solo"
        DecisionEffect.CANDIDATE_CREATED -> "Quedó como candidato"
        DecisionEffect.DUPLICATE -> "Ya existía"
        DecisionEffect.DISCARDED -> "Descartado"
        DecisionEffect.SHADOW_ONLY -> "Solo medido (Sombra)"
        DecisionEffect.FALLBACK_LEGACY -> "Salió como siempre"
        DecisionEffect.UNUSABLE -> "No se pudo leer el importe"
        DecisionEffect.GAVE_UP -> "No se pudo analizar"
    }

    fun categorySourceLabel(source: CategorySource) = when (source) {
        CategorySource.MEMORY -> "de tu historial con ese comercio"
        CategorySource.MODEL -> "elegida por el modelo"
        CategorySource.DEFAULT -> "por defecto"
    }

    fun reasonText(reason: DecisionReason) = when (reason) {
        DecisionReason.PARSER_IGNORED -> "Un parser a medida lo descartó a propósito (por ejemplo, un ingreso)."
        DecisionReason.NOT_A_SPEND -> "El modelo lo leyó como un ingreso, un reembolso o un aviso que no es un gasto, y las dos lecturas coincidieron."
        DecisionReason.NOT_COMPLETED_TERMINAL -> "La operación figura como rechazada o anulada."
        DecisionReason.PARSER_CONFLICT_MOVEMENT -> "Un parser a medida lo vio como gasto y el modelo no."
        DecisionReason.AMBIGUOUS_DIRECTION -> "No quedó claro si fue un gasto o un ingreso."
        DecisionReason.NOT_COMPLETED -> "La operación no figura como completada (pendiente o sin estado claro)."
        DecisionReason.MODEL_DOUBT -> "El propio modelo declaró dudas."
        DecisionReason.NO_SINGLE_OPERATION_AMOUNT -> "No hay un único importe claro de la operación (hay varios, o ninguno)."
        DecisionReason.INVALID_AMOUNT -> "El importe no es válido."
        DecisionReason.AMOUNT_NOT_IN_TEXT -> "El importe que dijo el modelo no aparece en el aviso."
        DecisionReason.CURRENCY_MISSING -> "No se pudo determinar la moneda."
        DecisionReason.CURRENCY_NOT_AUTO -> "La moneda no es guaraníes: esas no se registran solas."
        DecisionReason.CURRENCY_NOT_IN_TEXT -> "El aviso habla de otra moneda, no de guaraníes."
        DecisionReason.MERCHANT_MISSING -> "No se encontró el comercio."
        DecisionReason.MERCHANT_NOT_IN_TEXT -> "El comercio que dijo el modelo no aparece en el aviso."
        DecisionReason.PASSES_DISAGREE -> "Las dos lecturas del modelo no coincidieron."
        DecisionReason.NO_CROSS_CHECK -> "No se pudo hacer la segunda lectura de control."
        DecisionReason.PARSER_AMOUNT_MISMATCH -> "El importe del modelo no coincide con el del parser."
        DecisionReason.PARSER_CURRENCY_MISMATCH -> "La moneda del modelo no coincide con la del parser."
        DecisionReason.PARSER_MERCHANT_MISMATCH -> "El comercio del modelo no coincide con el del parser."
        DecisionReason.UNTRUSTED_SOURCE -> "La fuente no es de fiar para registrar sola (por ejemplo un correo sin un parser a medida)."
    }

    /** Las razones por las que este aviso termino asi, en castellano y en el orden en que se evaluaron. */
    fun explain(audit: DecisionAudit, categoryLabel: String?): List<String> = buildList {
        when (audit.effect) {
            DecisionEffect.AUTO_REGISTERED ->
                add("Se registró solo: el modelo lo leyó dos veces con el mismo resultado y el importe figura en el aviso.")
            DecisionEffect.CANDIDATE_CREATED -> {
                add("Quedó para que decidas vos porque:")
                addAll(audit.reasons.map { "• " + reasonText(it) })
            }
            DecisionEffect.DISCARDED -> {
                addAll(audit.reasons.map { reasonText(it) })
                add("Si fue un error, tocá Recuperar y vuelve como candidato.")
            }
            DecisionEffect.DUPLICATE -> add("Ya había un gasto o candidato igual (por ejemplo, el mismo aviso por otro canal).")
            DecisionEffect.SHADOW_ONLY -> add(
                "Modo Sombra: no se tocó nada. " + when (audit.outcome) {
                    RegistrationOutcome.AUTO_REGISTER -> "El modelo lo habría registrado solo."
                    RegistrationOutcome.REVIEW -> "El modelo lo habría dejado como candidato."
                    RegistrationOutcome.DISCARD -> "El modelo lo habría descartado."
                    null -> "El modelo no llegó a decidir."
                },
            )
            DecisionEffect.FALLBACK_LEGACY -> add("El modelo no pudo analizarlo (${audit.errorClass ?: "error"}) y salió como siempre, con el parser.")
            DecisionEffect.UNUSABLE -> add("Se avisó a tu teléfono: el aviso hablaba de dinero pero no se pudo leer un importe.")
            DecisionEffect.GAVE_UP -> add("No se pudo analizar (${audit.errorClass ?: "error"}) y no había parser: queda solo este registro.")
        }
        if (categoryLabel != null && audit.categorySource != null) {
            add("Categoría: $categoryLabel (${categorySourceLabel(audit.categorySource!!)}).")
        }
    }
}
