package com.eliasgonzalez.expensetracker.domain.analysis

/**
 * SHADOW: el flujo visible sigue siendo el de siempre; el modelo solo mide y audita.
 * ASSISTED: el modelo propone, siempre como candidato (nunca registra solo).
 * AUTOMATIC: registra solo lo seguro, el resto va a candidato.
 */
enum class AnalysisMode { SHADOW, ASSISTED, AUTOMATIC }

fun interface AnalysisModeProvider {
    fun currentMode(): AnalysisMode
}
