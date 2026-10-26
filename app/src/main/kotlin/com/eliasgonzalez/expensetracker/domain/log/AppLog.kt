package com.eliasgonzalez.expensetracker.domain.log

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * Destino de los logs: a donde se escribe lo que se registra con [AppLog].
 * Hoy es logcat (logging/LogcatSink); pasar a un archivo, a un servicio
 * externo o a varios a la vez es escribir otra implementacion de esta
 * interfaz y cambiar la linea que la instala en ExpenseTrackerApp: el
 * resto del codigo solo conoce AppLog y no se entera.
 */
interface LogSink {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?)

    /** Descarta todo. Es el destino hasta que alguien instale otro (ej. en tests). */
    object None : LogSink {
        override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) = Unit
    }
}

/**
 * Punto unico de logging de la app. Es una fachada global (como Timber o el
 * Logger de Java) a proposito: los Service/Activity los crea el sistema y la
 * DI de la app es manual, asi que pasar un logger por constructor a cada uno
 * seria mucho ruido para algo transversal. El destino se instala una vez con
 * [install]; los tests instalan un destino que registra las llamadas.
 *
 * REGLA DE PRIVACIDAD: nunca registrar montos, comercios, descripciones ni el
 * texto de las notificaciones (son datos financieros del usuario). Si, tipos de
 * error, codigos de respuesta, ids y nombres de componentes.
 *
 * Un logger no puede romper lo que esta registrando: si el destino lanza una
 * excepcion, se descarta en silencio.
 */
object AppLog {
    @Volatile
    private var sink: LogSink = LogSink.None

    fun install(sink: LogSink) {
        this.sink = sink
    }

    fun d(tag: String, message: String) = write(LogLevel.DEBUG, tag, message, null)

    fun i(tag: String, message: String) = write(LogLevel.INFO, tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        write(LogLevel.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        write(LogLevel.ERROR, tag, message, throwable)

    private fun write(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        try {
            sink.log(level, tag, message, throwable)
        } catch (ignored: Exception) {
            // Un fallo del destino nunca debe propagarse a quien esta logueando.
        }
    }
}
