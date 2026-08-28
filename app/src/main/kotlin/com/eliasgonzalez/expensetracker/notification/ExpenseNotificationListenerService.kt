package com.eliasgonzalez.expensetracker.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.ui.EXTRA_EDIT_CANDIDATE_ID
import com.eliasgonzalez.expensetracker.ui.QuickAddActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

const val CHANNEL_ID_CANDIDATES = "expense_candidates"
const val ACTION_ACCEPT = "com.eliasgonzalez.expensetracker.ACTION_ACCEPT"
const val ACTION_REJECT = "com.eliasgonzalez.expensetracker.ACTION_REJECT"
const val EXTRA_CANDIDATE_ID = "candidate_id"

class ExpenseNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == applicationContext.packageName) return

        val extras = sbn.notification.extras
        val context = NotificationContext(
            packageName = sbn.packageName,
            applicationName = appLabelFor(sbn.packageName),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
            timestamp = sbn.postTime,
        )

        val result = ParserEngine.parse(context) ?: return

        val candidate = ExpenseCandidate(
            amount = result.amount,
            currency = result.currency,
            merchant = result.merchant,
            merchantConfident = result.merchantConfident,
            occurredAt = context.timestamp,
            detectedAt = context.timestamp,
            sourceType = ExpenseSource.NOTIFICATION,
            sourceApp = context.applicationName,
            parserId = result.parserId,
            confidence = result.confidence,
        )
        scope.launch {
            val id = ServiceLocator.get().createCandidate(candidate) ?: return@launch
            showCandidateNotification(candidate.copy(id = id))
        }
    }

    private fun appLabelFor(packageName: String): String = try {
        val pm = applicationContext.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        packageName
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID_CANDIDATES,
            "Gastos detectados",
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)
    }

    private fun showCandidateNotification(candidate: ExpenseCandidate) {
        val acceptIntent = actionPendingIntent(ACTION_ACCEPT, candidate.id)
        val rejectIntent = actionPendingIntent(ACTION_REJECT, candidate.id)
        val editIntent = editActivityPendingIntent(candidate.id)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_CANDIDATES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💳 Gasto detectado")
            .setContentText("${candidate.merchant} — ₲${"%,d".format(candidate.amount)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Aceptar", acceptIntent)
            .addAction(0, "Editar", editIntent)
            .addAction(0, "Rechazar", rejectIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(candidate.id.toInt(), notification)
    }

    private fun actionPendingIntent(action: String, candidateId: Long): PendingIntent {
        val intent = Intent(this, CandidateActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_CANDIDATE_ID, candidateId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(this, requestCodeFor(candidateId, action), intent, flags)
    }

    private fun editActivityPendingIntent(candidateId: Long): PendingIntent {
        val intent = Intent(this, QuickAddActivity::class.java).apply {
            putExtra(EXTRA_EDIT_CANDIDATE_ID, candidateId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, requestCodeFor(candidateId, "edit_activity"), intent, flags)
    }

    // `candidateId.toInt() * 10 + ...` truncaba el Long a Int y podia
    // colisionar (dos candidateId distintos, mismo request code) para
    // ids por encima de ~2 mil millones - con FLAG_UPDATE_CURRENT eso
    // pisaria los extras de un PendingIntent con los del otro. hashCode()
    // de un string combinado no tiene ese problema de truncamiento
    // aritmetico (aunque en teoria puede colisionar, es astronomicamente
    // menos probable que la aritmetica simple de antes).
    private fun requestCodeFor(candidateId: Long, tag: String): Int =
        "$candidateId:$tag".hashCode()
}
