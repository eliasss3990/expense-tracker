package com.eliasgonzalez.expensetracker.notification

interface NotificationParser {
    fun canHandle(context: NotificationContext): Boolean
    fun parse(context: NotificationContext): ParseResult?

    /**
     * true si el parser RECONOCE la notificacion y la descarta a proposito (ej. un ingreso). Es una
     * regla curada y confiable, distinta de "no pude leerla": ParserEngine la distingue para que el
     * analisis con IA no la reviva.
     */
    fun explicitlyIgnores(context: NotificationContext): Boolean = false
}
