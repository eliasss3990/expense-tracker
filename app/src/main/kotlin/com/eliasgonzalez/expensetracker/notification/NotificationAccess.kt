package com.eliasgonzalez.expensetracker.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat

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
