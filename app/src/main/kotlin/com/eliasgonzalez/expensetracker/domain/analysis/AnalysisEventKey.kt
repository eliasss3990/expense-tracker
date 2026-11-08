package com.eliasgonzalez.expensetracker.domain.analysis

import java.security.MessageDigest

/** Huella estable de un aviso: sirve de clave de idempotencia y de referencia en logs (sin texto). */
object AnalysisEventKey {
    fun of(notification: NotificationSnapshot): String {
        val raw = listOf(
            notification.packageName,
            notification.timestamp.toString(),
            notification.title,
            notification.text,
            notification.bigText,
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** Version corta para logs. */
    fun short(key: String): String = key.take(8)
}
