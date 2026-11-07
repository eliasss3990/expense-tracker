package com.eliasgonzalez.expensetracker.notification
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.analysis.NotificationSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.ParserSnapshot
import com.eliasgonzalez.expensetracker.domain.analysis.TransactionAnalysisInput
import com.eliasgonzalez.expensetracker.domain.log.AppLog

const val CHANNEL_ID_CANDIDATES = "expense_candidates"
const val ACTION_ACCEPT = "com.eliasgonzalez.expensetracker.ACTION_ACCEPT"
const val ACTION_REJECT = "com.eliasgonzalez.expensetracker.ACTION_REJECT"
const val EXTRA_CANDIDATE_ID = "candidate_id"

class ExpenseNotificationListenerService : NotificationListenerService() {

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

        // Mantener la allowlist actual: IA puede analizar aunque el parser devuelva null,
        // pero no recibe notificaciones de aplicaciones ajenas a las fuentes financieras.
        if (!ParserEngine.isEligibleForAnalysis(context)) return

        val parserResult = ParserEngine.parse(context)
        val input = TransactionAnalysisInput(
            notification = NotificationSnapshot(
                packageName = context.packageName,
                applicationName = context.applicationName,
                title = context.title,
                text = context.text,
                bigText = context.bigText,
                timestamp = context.timestamp,
            ),
            parserResult = parserResult?.let {
                ParserSnapshot(
                    amount = it.amount,
                    currency = it.currency,
                    merchant = it.merchant,
                    merchantConfident = it.merchantConfident,
                    parserId = it.parserId,
                    confidence = it.confidence,
                )
            },
        )
        ServiceLocator.get().enqueueNotificationAnalysis(input)
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
