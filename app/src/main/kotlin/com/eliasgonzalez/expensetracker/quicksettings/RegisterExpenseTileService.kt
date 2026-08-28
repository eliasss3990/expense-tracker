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
 *
 * Siempre se pasa por una Activity invisible (QuickAddTrampolineActivity)
 * en vez de llamar startService() directo, porque solo
 * startActivityAndCollapse() cierra el panel de Ajustes rápidos - un
 * TileService que solo arranca un Service deja el panel abierto.
 */
class RegisterExpenseTileService : TileService() {

    override fun onClick() {
        super.onClick()

        val targetIntent = if (Settings.canDrawOverlays(this)) {
            Intent(this, QuickAddTrampolineActivity::class.java)
        } else {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        }.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    targetIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(targetIntent)
        }
    }
}
