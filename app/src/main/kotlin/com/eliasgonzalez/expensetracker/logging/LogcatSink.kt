package com.eliasgonzalez.expensetracker.logging

import android.util.Log
import com.eliasgonzalez.expensetracker.domain.log.LogLevel
import com.eliasgonzalez.expensetracker.domain.log.LogSink

/**
 * Escribe en logcat (visible con `adb logcat` o en Android Studio). OJO: logcat
 * es un bufer circular en memoria que se pierde al reiniciar el celular; sirve
 * para depurar con el equipo conectado, no para investigar algo que paso ayer.
 * Para eso habria que sumar otro LogSink que escriba a un archivo.
 */
class LogcatSink : LogSink {

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        Log.println(priorityFor(level), tag.take(MAX_TAG_LENGTH), format(message, throwable))
    }

    private fun format(message: String, throwable: Throwable?): String {
        if (throwable == null) return message
        // Se escribe el tipo y el mensaje a mano ademas del stack trace: segun la
        // documentacion de Log.getStackTraceString, para UnknownHostException
        // devuelve un texto vacio (evita ruido), y sin esto se perderia justo el
        // error de "sin conexion".
        return buildString {
            append(message)
            append(" [").append(throwable.javaClass.name)
            throwable.message?.let { append(": ").append(it) }
            append("]\n").append(Log.getStackTraceString(throwable))
        }
    }

    private fun priorityFor(level: LogLevel): Int = when (level) {
        LogLevel.DEBUG -> Log.DEBUG
        LogLevel.INFO -> Log.INFO
        LogLevel.WARN -> Log.WARN
        LogLevel.ERROR -> Log.ERROR
    }

    private companion object {
        // Limite historico de Android para la etiqueta (antes de API 26 lanzaba si pasaba de 23).
        const val MAX_TAG_LENGTH = 23
    }
}
