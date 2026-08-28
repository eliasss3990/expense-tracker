package com.eliasgonzalez.expensetracker.ui.onboarding

import android.content.Context

private const val PREFS_NAME = "onboarding_prefs"
private const val KEY_PERMISSIONS_ONBOARDING_COMPLETED = "permissions_onboarding_completed"

/**
 * Recuerda si el usuario ya pasó por la pantalla de permisos, para no
 * mostrarla de nuevo en cada apertura - "Continuar" (con o sin otorgar
 * todo) la marca como completada; después de eso, cualquier permiso que
 * siga faltando se recuerda con el banner del Dashboard, no con esta
 * pantalla de nuevo.
 */
object OnboardingPrefs {
    fun isPermissionsOnboardingCompleted(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERMISSIONS_ONBOARDING_COMPLETED, false)

    fun markPermissionsOnboardingCompleted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PERMISSIONS_ONBOARDING_COMPLETED, true)
            .apply()
    }
}
