package com.eliasgonzalez.expensetracker.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisEvent
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisEventKey
import com.eliasgonzalez.expensetracker.domain.analysis.NotificationSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.ParserEvidence
import com.eliasgonzalez.expensetracker.domain.analysis.SourceTrust
import com.eliasgonzalez.expensetracker.domain.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Solo traduce la notificacion del sistema y filtra lo que no es financiero. Que se hace con ella
 * (candidato de siempre, analisis con el modelo, registro automatico) lo decide el dominio en
 * HandleDetectedNotification.
 */
class ExpenseNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AndroidCandidateAnnouncer.ensureChannel(applicationContext)
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

        val parser = ParserEngine.evidence(context)
        // Un descarte explicito de un parser curado (ej. un ingreso) es definitivo: ni se analiza.
        if (parser is ParserEvidence.ExplicitlyIgnored) return
        val trust = TrustedSources.trustOf(context.packageName)
        // Sin parser, solo se analiza una fuente de la lista que ademas hable de dinero.
        if (parser is ParserEvidence.NoParser && (trust == null || !FinancialPrefilter.mentionsMoney(context))) return

        val snapshot = NotificationSnapshot(
            packageName = context.packageName,
            applicationName = context.applicationName,
            title = context.title,
            text = context.text,
            bigText = context.bigText,
            timestamp = context.timestamp,
        )
        val event = AnalysisEvent(AnalysisEventKey.of(snapshot), snapshot, parser, trust ?: SourceTrust.UNTRUSTED)
        scope.launch { ServiceLocator.get().handleDetectedNotification(event) }
    }

    private fun appLabelFor(packageName: String): String = try {
        val pm = applicationContext.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        AppLog.w("NotifListener", "No se pudo leer el nombre de la app de origen", e)
        packageName
    }
}
