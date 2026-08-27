package com.eliasgonzalez.expensetracker.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Recibe Aceptar/Rechazar desde la notificación propia. La idempotencia
 * (ignorar una acción sobre un candidato que ya no está PENDING) vive en
 * los casos de uso ConfirmCandidate/RejectCandidate, no acá.
 */
class CandidateActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val candidateId = intent.getLongExtra(EXTRA_CANDIDATE_ID, -1)
        if (candidateId < 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_ACCEPT -> ServiceLocator.get().confirmCandidate(candidateId)
                    ACTION_REJECT -> ServiceLocator.get().rejectCandidate(candidateId)
                    else -> return@launch
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(candidateId.toInt())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
