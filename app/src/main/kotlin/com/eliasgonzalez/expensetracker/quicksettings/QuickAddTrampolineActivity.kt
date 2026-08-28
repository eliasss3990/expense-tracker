package com.eliasgonzalez.expensetracker.quicksettings

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Activity invisible: existe solo para que `startActivityAndCollapse`
 * cierre el panel de Ajustes rápidos. Un `TileService` no tiene forma de
 * colapsar el panel si solo arranca un Service (no existe un
 * `collapsePanels()` público en esta API) - lanzando esta Activity de
 * paso conseguimos ese cierre "gratis" y arrancamos el overlay real
 * desde acá.
 */
class QuickAddTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, QuickAddOverlayService::class.java))
        overridePendingTransition(0, 0)
        finish()
    }
}
