package com.eliasgonzalez.expensetracker.data.analysis

import android.content.Context
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisMode
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisModeProvider

class SharedPreferencesAnalysisModeProvider(context: Context) : AnalysisModeProvider {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun currentMode(): AnalysisMode =
        runCatching {
            AnalysisMode.valueOf(preferences.getString(KEY_MODE, null) ?: AnalysisMode.SHADOW.name)
        }.getOrDefault(AnalysisMode.SHADOW)

    fun setMode(mode: AnalysisMode) {
        preferences.edit().putString(KEY_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFERENCES = "notification_analysis"
        const val KEY_MODE = "mode"
    }
}
