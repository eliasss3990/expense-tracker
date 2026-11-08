package com.eliasgonzalez.expensetracker.domain.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Programa UN reintento de [drain] dentro de N ms. Mientras haya uno esperando, otro pedido se ignora (no se
 * apilan); pero apenas el drain programado ARRANCA, ya se puede pedir el siguiente.
 *
 * Esto ultimo es lo que un chequeo ingenuo ("hay un job activo, no hago nada") rompia: el reintento se pide
 * DESDE el drain que corre dentro del propio job, que sigue activo, asi que ningun reintento posterior se
 * programaba y el aviso quedaba pendiente hasta la proxima notificacion o reiniciar la app.
 */
class AnalysisRetryScheduler(
    private val scope: CoroutineScope,
    private val drain: suspend () -> Unit,
) {
    private val lock = Any()
    private var waiting: Job? = null

    fun schedule(delayMillis: Long) {
        synchronized(lock) {
            if (waiting?.isActive == true) return
            waiting = scope.launch {
                delay(delayMillis)
                synchronized(lock) { waiting = null } // desde aca ya cuenta como "corriendo", no "esperando"
                drain()
            }
        }
    }
}
