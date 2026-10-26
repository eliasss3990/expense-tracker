package com.eliasgonzalez.expensetracker.notification

import android.app.NotificationManager
import android.content.Context
import com.eliasgonzalez.expensetracker.domain.notification.CandidateNotifier

/**
 * El id de la notificacion es `candidate.id.toInt()` (ver
 * ExpenseNotificationListenerService.showCandidateNotification); tiene
 * que seguir siendo el mismo valor acá o el cancel no encuentra nada.
 */
class AndroidCandidateNotifier(context: Context) : CandidateNotifier {
    private val appContext = context.applicationContext

    override fun dismiss(candidateId: Long) {
        // Retirar una notificacion es accesorio: si falla, el gasto ya quedo
        // resuelto y no debe romper el caso de uso (ni crashear al receiver).
        runCatching {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notificationIdFor(candidateId))
        }
    }

    companion object {
        fun notificationIdFor(candidateId: Long): Int = candidateId.toInt()
    }
}
