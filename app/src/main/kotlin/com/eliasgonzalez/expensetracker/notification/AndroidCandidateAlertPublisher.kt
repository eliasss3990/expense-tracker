package com.eliasgonzalez.expensetracker.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.eliasgonzalez.expensetracker.R
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.notification.CandidateAlertPublisher
import com.eliasgonzalez.expensetracker.ui.EXTRA_EDIT_CANDIDATE_ID
import com.eliasgonzalez.expensetracker.ui.QuickAddActivity

/** Publicador de alertas desacoplado del ciclo de vida de NotificationListenerService. */
class AndroidCandidateAlertPublisher(context: Context) : CandidateAlertPublisher {
    private val appContext = context.applicationContext

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_CANDIDATES,
                    "Gastos detectados",
                    NotificationManager.IMPORTANCE_HIGH,
                )
            )
        }
    }

    override fun publish(candidate: ExpenseCandidate) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID_CANDIDATES)
            .setSmallIcon(R.drawable.ic_stat_expense)
            .setColor(ContextCompat.getColor(appContext, R.color.brand_primary))
            .setContentTitle("💳 Gasto detectado")
            .setContentText("${candidate.merchant} — ₲${"%,d".format(candidate.amount)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Aceptar", actionPendingIntent(ACTION_ACCEPT, candidate.id))
            .addAction(0, "Editar", editPendingIntent(candidate.id))
            .addAction(0, "Rechazar", actionPendingIntent(ACTION_REJECT, candidate.id))
            .build()

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(AndroidCandidateNotifier.notificationIdFor(candidate.id), notification)
    }

    private fun actionPendingIntent(action: String, candidateId: Long): PendingIntent {
        val intent = Intent(appContext, CandidateActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_CANDIDATE_ID, candidateId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCodeFor(candidateId, action),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun editPendingIntent(candidateId: Long): PendingIntent {
        val intent = Intent(appContext, QuickAddActivity::class.java).apply {
            putExtra(EXTRA_EDIT_CANDIDATE_ID, candidateId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            appContext,
            requestCodeFor(candidateId, "edit_activity"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCodeFor(candidateId: Long, tag: String): Int = "$candidateId:$tag".hashCode()
}
