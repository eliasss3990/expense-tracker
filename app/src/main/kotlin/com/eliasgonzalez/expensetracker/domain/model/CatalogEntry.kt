package com.eliasgonzalez.expensetracker.domain.model

/**
 * Tipos de catalogo conocidos. `kind` es un String (no un enum) a
 * proposito: el catalogo es generico y sumar otra lista gestionable
 * (ej. medios de pago) es agregar una constante aca, sin tocar
 * repositorio, casos de uso ni persistencia.
 */
object CatalogKind {
    const val CATEGORY = "category"
}

/**
 * Una entrada de una lista gestionable por el usuario.
 *
 * - `id` es estable y nunca se deriva del nombre: renombrar no rompe los
 *   gastos que ya lo referencian (guardan el id).
 * - `active = false` la saca de los selectores para ELEGIR, pero los
 *   gastos viejos siguen mostrandola con su nombre y color de siempre.
 * - `builtIn`: viene de fabrica (seed), no la creo el usuario.
 * - `protected`: no se puede desactivar ni eliminar (ej. el comodin
 *   "Otro"). Es un dato de la entrada, no una regla fija por id, para
 *   que cada tipo de catalogo decida cual es su comodin.
 * - `color` en `#RRGGBB`.
 */
data class CatalogEntry(
    val kind: String,
    val id: String,
    val label: String,
    val color: String,
    val active: Boolean = true,
    val sortOrder: Int = 0,
    val builtIn: Boolean = false,
    val protected: Boolean = false,
)
