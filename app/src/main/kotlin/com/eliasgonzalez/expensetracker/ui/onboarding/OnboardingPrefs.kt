package com.eliasgonzalez.expensetracker.ui.onboarding

import android.content.Context

private const val PREFS_NAME = "onboarding_prefs"
private const val KEY_PERMISSIONS_ONBOARDING_COMPLETED = "permissions_onboarding_completed"
private const val KEY_DISMISSED_PERMISSIONS = "dismissed_missing_permissions"

/** Ids estables de cada permiso que puede faltar - se usan para
 * recordar cuáles deslizó el usuario para ocultar del banner. */
const val PERMISSION_KEY_NOTIFICATION_LISTENER = "notification_listener"
const val PERMISSION_KEY_POST_NOTIFICATIONS = "post_notifications"

/**
 * Recuerda si el usuario ya pasó por la pantalla de permisos, para no
 * mostrarla de nuevo en cada apertura - "Continuar" (con o sin otorgar
 * todo) la marca como completada; después de eso, cualquier permiso que
 * siga faltando se recuerda con el banner del Dashboard, no con esta
 * pantalla de nuevo.
 *
 * También recuerda qué avisos de permiso faltante el usuario deslizó
 * para ocultar (como una notificación) - siguen accesibles desde el
 * menú "Permisos" del Dashboard aunque estén ocultos del banner. Si el
 * permiso se otorga más tarde y luego se revoca, el aviso vuelve a
 * aparecer (ver limpiarSiOtorgado en MainActivity).
 */
object OnboardingPrefs {
    fun isPermissionsOnboardingCompleted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PERMISSIONS_ONBOARDING_COMPLETED, false)

    fun markPermissionsOnboardingCompleted(context: Context) {
        prefs(context).edit().putBoolean(KEY_PERMISSIONS_ONBOARDING_COMPLETED, true).apply()
    }

    fun getDismissedPermissionKeys(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_DISMISSED_PERMISSIONS, emptySet()).orEmpty()

    fun dismissPermission(context: Context, key: String) {
        val current = getDismissedPermissionKeys(context)
        prefs(context).edit().putStringSet(KEY_DISMISSED_PERMISSIONS, current + key).apply()
    }

    fun clearPermissionDismissal(context: Context, key: String) {
        val current = getDismissedPermissionKeys(context)
        if (key in current) {
            prefs(context).edit().putStringSet(KEY_DISMISSED_PERMISSIONS, current - key).apply()
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
