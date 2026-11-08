package com.eliasgonzalez.expensetracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.eliasgonzalez.expensetracker.R
import com.eliasgonzalez.expensetracker.domain.analysis.CandidateAnnouncer
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.ui.EXTRA_EDIT_CANDIDATE_ID
import com.eliasgonzalez.expensetracker.ui.QuickAddActivity

const val CHANNEL_ID_CANDIDATES = "expense_candidates"
const val ACTION_ACCEPT = "com.eliasgonzalez.expensetracker.ACTION_ACCEPT"
const val ACTION_REJECT = "com.eliasgonzalez.expensetracker.ACTION_REJECT"
const val EXTRA_CANDIDATE_ID = "candidate_id"

/**
 * Publica la notificacion "Gasto detectado" con Aceptar / Editar / Rechazar. Antes vivia dentro del
 * servicio de escucha; se extrajo para que tambien la use el procesador de la cola (que corre fuera del
 * servicio) sin duplicar nada. El id es el de AndroidCandidateNotifier.notificationIdFor: tiene que ser
 * el mismo al publicar y al retirar.
 */
class AndroidCandidateAnnouncer(context: Context) : CandidateAnnouncer {
    private val appContext = context.applicationContext

    override suspend fun announce(candidate: ExpenseCandidate) {
        ensureChannel(appContext)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID_CANDIDATES)
            // Silueta de la marca (ic_stat_expense), no el icono generico del sistema; el color de marca
            // se pinta con setColor (Android tine el circulo del icono en la bandeja expandida).
            .setSmallIcon(R.drawable.ic_stat_expense)
            .setColor(ContextCompat.getColor(appContext, R.color.brand_primary))
            .setContentTitle("💳 Gasto detectado")
            .setContentText("${candidate.merchant} — ₲${"%,d".format(candidate.amount)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Aceptar", actionPendingIntent(ACTION_ACCEPT, candidate.id))
            .addAction(0, "Editar", editActivityPendingIntent(candidate.id))
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
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(appContext, requestCodeFor(candidateId, action), intent, flags)
    }

    private fun editActivityPendingIntent(candidateId: Long): PendingIntent {
        val intent = Intent(appContext, QuickAddActivity::class.java).apply {
            putExtra(EXTRA_EDIT_CANDIDATE_ID, candidateId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(appContext, requestCodeFor(candidateId, "edit_activity"), intent, flags)
    }

    // `candidateId.toInt() * 10 + ...` truncaba el Long a Int y podia colisionar (dos candidateId distintos,
    // mismo request code): con FLAG_UPDATE_CURRENT eso pisaria los extras de un PendingIntent con los
    // del otro. Un hash del string combinado no tiene ese problema de truncamiento aritmetico.
    private fun requestCodeFor(candidateId: Long, tag: String): Int = "$candidateId:$tag".hashCode()

    companion object {
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID_CANDIDATES, "Gastos detectados", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }
}
