package com.eliasgonzalez.expensetracker.data.analysis

import android.content.Context
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisMode
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisModeStore

/** Persiste el modo de analisis. Arranca en Sombra: el flujo visible no cambia hasta que el usuario lo decide. */
class SharedPreferencesAnalysisModeProvider(context: Context) : AnalysisModeStore {
    private val preferences = context.applicationContext.getSharedPreferences("analysis_mode", Context.MODE_PRIVATE)

    override fun currentMode(): AnalysisMode =
        AnalysisMode.entries.firstOrNull { it.name == preferences.getString(KEY, null) } ?: AnalysisMode.SHADOW

    override fun setMode(mode: AnalysisMode) {
        preferences.edit().putString(KEY, mode.name).apply()
    }

    private companion object {
        const val KEY = "mode"
    }
}
