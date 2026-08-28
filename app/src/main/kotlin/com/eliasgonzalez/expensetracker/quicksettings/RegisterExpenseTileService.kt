package com.eliasgonzalez.expensetracker.quicksettings

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.TileService

/**
 * Tocar el tile "Gasto" abre una ventanita flotante y movible
 * (QuickAddOverlayService) en vez de una pantalla completa. Requiere el
 * permiso especial "Mostrar sobre otras apps" - si falta, lleva directo
 * a esa pantalla de Ajustes en vez de fallar en silencio.
 */
class RegisterExpenseTileService : TileService() {

    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        startService(Intent(this, QuickAddOverlayService::class.java))
    }
}
