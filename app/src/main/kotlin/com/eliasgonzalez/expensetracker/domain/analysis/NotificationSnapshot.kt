package com.eliasgonzalez.expensetracker.domain.analysis

/** Foto minima de una notificacion. No depende de Android. */
data class NotificationSnapshot(
    val packageName: String,
    val applicationName: String,
    val title: String,
    val text: String,
    val bigText: String,
    val timestamp: Long,
) {
    fun searchableText(): String = listOf(title, text, bigText)
        .filter(String::isNotBlank)
        .joinToString("\n")
}
