package com.eliasgonzalez.expensetracker.update

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Por que fallo la consulta de la ultima version. Distingue casos que antes se veian todos igual. */
enum class UpdateFailure {
    /** GitHub limita la API sin autenticar a 60 consultas por hora por IP. */
    RATE_LIMITED,
    HTTP_ERROR,
    NO_NETWORK,
    TIMEOUT,

    /** Fallo de TLS: tipicamente fecha/hora incorrecta en el celular, o un proxy. */
    TLS_ERROR,
    BAD_RESPONSE,
    UNKNOWN,
}

sealed interface UpdateCheckResult {
    data class Found(val release: ReleaseInfo) : UpdateCheckResult
    data class Failed(val reason: UpdateFailure, val detail: String) : UpdateCheckResult
}

internal fun classifyHttpFailure(code: Int, rateLimitRemaining: String?): UpdateFailure = when {
    code == 429 -> UpdateFailure.RATE_LIMITED
    // GitHub responde 403 (no 429) cuando se agota el cupo anonimo, y lo avisa en este header.
    code == 403 && rateLimitRemaining?.trim() == "0" -> UpdateFailure.RATE_LIMITED
    else -> UpdateFailure.HTTP_ERROR
}

internal fun classifyException(e: Exception): UpdateFailure = when (e) {
    is UnknownHostException, is ConnectException, is NoRouteToHostException -> UpdateFailure.NO_NETWORK
    is SocketTimeoutException -> UpdateFailure.TIMEOUT
    // Va despues de los anteriores solo por claridad: SSLException no es subclase de ninguno.
    is SSLException -> UpdateFailure.TLS_ERROR
    else -> UpdateFailure.UNKNOWN
}

/** Texto para el usuario cuando "Buscar actualizaciones" falla. */
fun UpdateFailure.userMessage(): String = when (this) {
    UpdateFailure.RATE_LIMITED -> "GitHub limitó las consultas desde tu red; probá más tarde o con otra red"
    UpdateFailure.HTTP_ERROR -> "GitHub respondió con un error"
    UpdateFailure.NO_NETWORK -> "sin conexión a internet"
    UpdateFailure.TIMEOUT -> "GitHub tardó demasiado en responder"
    UpdateFailure.TLS_ERROR -> "error de conexión segura (revisá la fecha y hora del celular)"
    UpdateFailure.BAD_RESPONSE -> "respuesta inesperada de GitHub"
    UpdateFailure.UNKNOWN -> "error desconocido"
}
