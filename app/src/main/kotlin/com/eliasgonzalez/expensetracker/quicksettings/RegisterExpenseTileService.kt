package com.eliasgonzalez.expensetracker.quicksettings

import android.content.Intent
import android.service.quicksettings.TileService
import com.eliasgonzalez.expensetracker.ui.QuickAddActivity

class RegisterExpenseTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickAddActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
