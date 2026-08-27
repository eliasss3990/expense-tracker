package com.eliasgonzalez.expensetracker.notification

data class NotificationContext(
    val packageName: String,
    val applicationName: String,
    val title: String,
    val text: String,
    val bigText: String,
    val timestamp: Long,
)
