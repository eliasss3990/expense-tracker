package com.eliasgonzalez.expensetracker.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.eliasgonzalez.expensetracker.R
import com.eliasgonzalez.expensetracker.domain.analysis.ReviewNoticeAnnouncer
import com.eliasgonzalez.expensetracker.ui.MainActivity

/**
 * Cuando llega un aviso con dinero que no se pudo interpretar (sin importe utilizable) no hay candidato que
 * mostrar, pero tampoco puede desaparecer en silencio: se avisa al usuario para que lo mire en su banco.
 * No lleva datos del aviso (ni importe ni comercio): solo la app de origen.
 */
class AndroidReviewNoticeAnnouncer(context: Context) : ReviewNoticeAnnouncer {
    private val appContext = context.applicationContext

    override suspend fun announce(sourceApp: String) {
        AndroidCandidateAnnouncer.ensureChannel(appContext)
        val open = PendingIntent.getActivity(
            appContext, NOTICE_REQUEST_CODE,
            Intent(appContext, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID_CANDIDATES)
            .setSmallIcon(R.drawable.ic_stat_expense)
            .setColor(ContextCompat.getColor(appContext, R.color.brand_primary))
            .setContentTitle("Aviso para revisar")
            .setContentText("$sourceApp: llegó un aviso con dinero que no pude interpretar. Revisalo en tu banco.")
            .setStyle(NotificationCompat.BigTextStyle())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private companion object {
        const val NOTICE_REQUEST_CODE = 7001
    }
}
