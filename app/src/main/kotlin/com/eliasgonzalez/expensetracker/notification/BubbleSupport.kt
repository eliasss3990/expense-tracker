package com.eliasgonzalez.expensetracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.eliasgonzalez.expensetracker.ui.QuickAddActivity

/**
 * Android Bubbles (ventanita chica flotante y movible, como las de
 * Messenger) exige, en Android 11+, que la notificación simule una
 * "conversación": un Person + un shortcut dinámico. La app no es de
 * chat, pero es la única forma que el sistema ofrece para esto — se
 * registra un shortcut fijo ("Expense Tracker") en vez de uno por
 * contacto real.
 */
object BubbleSupport {
    private const val CHANNEL_ID = "expense_quick_add_bubble"
    private const val SHORTCUT_ID = "expense_tracker_quickadd"
    const val NOTIFICATION_ID = 990_001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alta rápida (burbuja)",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    fun ensureShortcut(context: Context) {
        val icon = IconCompat.createWithResource(context, android.R.drawable.ic_dialog_info)
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setLongLived(true)
            .setShortLabel("Expense Tracker")
            .setIcon(icon)
            .setIntent(Intent(Intent.ACTION_VIEW).setPackage(context.packageName))
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    fun buildQuickAddBubbleNotification(context: Context): android.app.Notification {
        // Las Bubbles exigen que el PendingIntent sea mutable (a diferencia
        // de casi todo el resto de la app) - con FLAG_IMMUTABLE el sistema
        // rechaza la notificación con IllegalArgumentException.
        val bubbleIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, QuickAddActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val bubbleIcon = IconCompat.createWithResource(context, android.R.drawable.ic_dialog_info)
        val bubbleMetadata = NotificationCompat.BubbleMetadata.Builder(bubbleIntent, bubbleIcon)
            .setDesiredHeight(400)
            .setAutoExpandBubble(true)
            .setSuppressNotification(true)
            .build()

        val person = Person.Builder()
            .setName("Expense Tracker")
            .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_dialog_info))
            .setImportant(true)
            .build()

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Nuevo gasto")
            .setContentText("Alta rápida")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setShortcutId(SHORTCUT_ID)
            .addPerson(person)
            .setBubbleMetadata(bubbleMetadata)
            .build()
    }
}
