package com.eliasgonzalez.expensetracker.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eliasgonzalez.expensetracker.data.CandidateStore
import com.eliasgonzalez.expensetracker.model.CandidateStatus

/**
 * Recibe Aceptar/Rechazar desde la notificación propia. Idempotente:
 * si el candidato ya no está PENDING, no hace nada (evita doble-confirmación).
 */
class CandidateActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val candidateId = intent.getLongExtra(EXTRA_CANDIDATE_ID, -1)
        if (candidateId < 0) return

        val candidate = CandidateStore.findById(candidateId) ?: return
        if (candidate.status != CandidateStatus.PENDING) return

        when (intent.action) {
            ACTION_ACCEPT -> CandidateStore.updateStatus(candidateId, CandidateStatus.ACCEPTED)
            ACTION_REJECT -> CandidateStore.updateStatus(candidateId, CandidateStatus.REJECTED)
            else -> return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(candidateId.toInt())
    }
}
