package com.eliasgonzalez.expensetracker.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * El acceso a notificaciones (NotificationListenerService) no se puede
 * pedir con un dialogo de permiso normal — Android obliga a que el
 * usuario lo confirme a mano en Ajustes por lo sensible que es. Lo maximo
 * que la app puede hacer es detectar si falta y llevar al usuario directo
 * a esa pantalla (ver openNotificationAccessSettings en MainActivity).
 */
fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
    return context.packageName in enabledPackages
}

/**
 * A diferencia del acceso a notificaciones, este SÍ es un permiso runtime
 * normal (Android 13+) - se pide con un diálogo del sistema. En versiones
 * anteriores no existe el permiso, así que se considera "otorgado".
 */
fun isPostNotificationsGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}
