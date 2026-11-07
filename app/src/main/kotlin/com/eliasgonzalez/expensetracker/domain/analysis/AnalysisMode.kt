package com.eliasgonzalez.expensetracker.domain.analysis

enum class AnalysisMode {
    /** Ejecuta IA para medir precision, pero mantiene exactamente el flujo legacy. */
    SHADOW,

    /** La IA puede proponer candidatos, pero nunca confirma gastos por si sola. */
    ASSISTED,

    /** Permite auto-registro cuando reconciliacion deterministica devuelve AUTO_REGISTER. */
    AUTOMATIC,
}

fun interface AnalysisModeProvider {
    fun currentMode(): AnalysisMode
}
