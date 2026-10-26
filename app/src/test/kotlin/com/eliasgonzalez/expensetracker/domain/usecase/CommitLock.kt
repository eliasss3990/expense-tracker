package com.eliasgonzalez.expensetracker.domain.usecase

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Toma el mutex y ejecuta [block] SIN que se pueda cancelar a medias.
 *
 * Un caso de uso que cambia estado y despues registra la actividad / retira la
 * notificacion es una unidad: no puede quedar partido. La UI lo lanza desde un
 * `rememberCoroutineScope()`, que Compose cancela cuando el composable sale de la pantalla,
 * y eso pasa justo cuando el cambio de estado saca la tarjeta/fila de la lista: en medio
 * del caso de uso. Sin esto se perdian la entrada de Actividad y el retiro de la
 * notificacion al resolver desde la Bandeja.
 *
 * La espera previa (ej. `awaitReady`) va FUERA, para seguir siendo cancelable. Aca solo
 * entra trabajo local y acotado (SQLite): nada que pueda colgarse, porque no se puede cancelar
 * y retendria el mutex compartido.
 *
 * Si el llamador ya estaba cancelado al llegar, no se ejecuta nada: `Mutex.lock` con el mutex
 * libre no mira la cancelacion, y sin el chequeo se aplicaria la accion de una pantalla que ya
 * no existe.
 */
internal suspend fun <T> Mutex.withCommitLock(block: suspend () -> T): T {
    currentCoroutineContext().ensureActive()
    return withLock { withContext(NonCancellable) { block() } }
}
