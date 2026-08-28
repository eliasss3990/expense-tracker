package com.eliasgonzalez.expensetracker.notification

interface NotificationParser {
    fun canHandle(context: NotificationContext): Boolean
    fun parse(context: NotificationContext): ParseResult?
}
