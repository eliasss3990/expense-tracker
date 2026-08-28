package com.eliasgonzalez.expensetracker.quicksettings

import android.app.NotificationManager
import android.service.quicksettings.TileService
import com.eliasgonzalez.expensetracker.notification.BubbleSupport

/**
 * Tocar el tile ya no abre QuickAddActivity en pantalla completa: postea
 * una notificación-burbuja que se auto-expande al instante, mostrando la
 * misma pantalla pero como ventanita chica y movible (estilo Now Bar).
 *
 * No hay una API pública para forzar el cierre del panel de Ajustes
 * rápidos sin arrancar una Activity (no existe `collapsePanels()` en
 * `TileService`) — el panel se cierra solo al tocar afuera, como con
 * cualquier otro tile que no navega a una pantalla.
 */
class RegisterExpenseTileService : TileService() {

    override fun onClick() {
        super.onClick()
        BubbleSupport.ensureChannel(this)
        BubbleSupport.ensureShortcut(this)
        val notification = BubbleSupport.buildQuickAddBubbleNotification(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(BubbleSupport.NOTIFICATION_ID, notification)
    }
}
