package com.eliasgonzalez.expensetracker.data.analysis

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Guarda UN recurso caro (el motor del modelo, ~2,6 GB de pesos) que se crea perezosamente y se cierra a mano.
 *
 * Detalle que importa: el recurso se asigna DENTRO de `withContext`. Crearlo bloquea y no se puede interrumpir; si la
 * corrutina que lo pidio se cancela mientras tanto (timeout, usuario), `withContext` descarta su resultado. Asignando
 * afuera (`withContext { create() }.also { value = it }`) el motor ya inicializado quedaba sin referencia y sin `close()`,
 * y el siguiente pedido cargaba otro mas: dos copias de los pesos en RAM.
 */
internal class ResourceHolder<T : Any>(
    private val dispatcher: CoroutineDispatcher,
    private val defaultCreate: () -> T,
    private val close: (T) -> Unit,
) {
    @Volatile
    private var value: T? = null

    fun current(): T? = value

    suspend fun get(create: () -> T = defaultCreate): T =
        value ?: withContext(dispatcher) { value ?: create().also { value = it } }

    suspend fun release() {
        withContext(dispatcher) {
            value?.let { runCatching { close(it) } }
            value = null
        }
    }
}
