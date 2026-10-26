package com.eliasgonzalez.expensetracker.domain.retry

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Ejecuta [block] hasta [attempts] veces, esperando [delayMillis] * (numero de
 * intento) entre uno y otro (backoff lineal). Devuelve true si algun intento
 * termino sin excepcion, false si fallaron todos. Cada fallo se informa por
 * [onFailure] (el llamador decide si lo loguea).
 *
 * La cancelacion de la corrutina NUNCA se reintenta ni se traga: se propaga.
 * Pensada para una operacion idempotente, como volver a cargar una lista desde
 * la base tras un fallo transitorio (base bloqueada un instante, por ejemplo).
 */
suspend fun retrying(
    attempts: Int,
    delayMillis: Long,
    onFailure: (attempt: Int, error: Exception) -> Unit = { _, _ -> },
    block: suspend () -> Unit,
): Boolean {
    require(attempts >= 1) { "attempts debe ser >= 1" }
    for (attempt in 1..attempts) {
        try {
            block()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(attempt, e)
            if (attempt < attempts) delay(delayMillis * attempt)
        }
    }
    return false
}
