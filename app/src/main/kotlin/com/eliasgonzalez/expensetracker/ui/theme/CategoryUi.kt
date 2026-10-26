package com.eliasgonzalez.expensetracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.catalog.CatalogDefaults
import com.eliasgonzalez.expensetracker.domain.catalog.categoryResolver
import com.eliasgonzalez.expensetracker.domain.log.AppLog
import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind

/**
 * Lo que la UI necesita para dibujar una categoria, armado desde una
 * entrada del catalogo, con `label`, `id` y `brandColor()`.
 *
 * `icon` es null para las categorias creadas por el usuario: el avatar
 * muestra entonces la inicial del nombre (no hay un ícono que asignar).
 */
@Immutable
data class CategoryUi(
    val id: String,
    val label: String,
    val color: Color,
    val icon: ImageVector?,
    val active: Boolean,
) {
    fun brandColor(): Color = color

    /** Texto de un chip de seleccion: las desactivadas se marcan como tales. */
    fun chipLabel(): String = if (active) label else "$label (inactiva)"
}

private val FALLBACK_COLOR = Color(0xFF6B7280)

/** Hex `#RRGGBB` a Color; uno inválido cae en gris en vez de romper. */
fun parseCatalogColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse {
        AppLog.w("CategoryUi", "Color de categoría inválido '$hex'", it)
        FALLBACK_COLOR
    }

fun CatalogEntry.toCategoryUi(): CategoryUi = CategoryUi(
    id = id,
    label = label,
    color = parseCatalogColor(color),
    // El ícono sigue atado al id de las categorias de fabrica: renombrarla
    // cambia el texto, no el dibujo.
    icon = builtInCategoryIcon(id),
    active = active,
)

@Composable
private fun rememberCatalogEntries(): List<CatalogEntry> {
    val container = ServiceLocator.get()
    val flow = remember { container.observeCatalog(CatalogKind.CATEGORY, onlyActive = false) }
    // La foto actual como valor inicial: con el catalogo ya cargado el primer frame
    // sale con las categorias reales (no con las de fabrica).
    val entries by flow.collectAsState(initial = container.observeCatalog.snapshot(CatalogKind.CATEGORY, onlyActive = false))
    // El catalogo nunca esta vacio de verdad ("Otro" no se puede borrar): una lista
    // vacia significa "todavia no cargo", y entonces se usan las de fabrica.
    return entries.ifEmpty { CatalogDefaults.categories }
}

/**
 * Categorias para ELEGIR en un selector: solo las activas. `alsoInclude`
 * suma ids que tienen que aparecer igual aunque esten desactivadas (la
 * categoria actual de lo que se esta editando, o las que tienen gastos en
 * un filtro): si no, el usuario veria el gasto sin ninguna marcada.
 */
@Composable
fun rememberCategoryOptions(alsoInclude: Set<String> = emptySet()): List<CategoryUi> {
    val entries = rememberCatalogEntries()
    return remember(entries, alsoInclude) {
        entries.filter { it.active || it.id in alsoInclude }.map { it.toCategoryUi() }
    }
}

/**
 * Resuelve una categoria por id para MOSTRARLA, incluso desactivada: los
 * gastos viejos conservan su nombre y color. Un id desconocido se muestra
 * como "Otro". Llamarlo UNA vez en el padre y pasar el resultado a las filas
 * (no una suscripcion al catalogo por fila).
 */
@Composable
fun rememberCategoryLookup(): (String) -> CategoryUi {
    val entries = rememberCatalogEntries()
    return remember(entries) {
        // Se arma UNA vez por cambio del catalogo (no por fila ni por consulta): un mapa
        // id -> CategoryUi ya construido, sin volver a parsear colores ni recorrer la lista.
        val resolve = entries.categoryResolver()
        val uiById = entries.associate { it.id to it.toCategoryUi() }
        val fallbackUi = resolve("").let { uiById[it.id] ?: it.toCategoryUi() }
        // Se asigna a una variable: un `{` en la linea siguiente se leeria como lambda final
        // de la llamada anterior.
        val lookup: (String) -> CategoryUi = { id -> uiById[resolve(id).id] ?: fallbackUi }
        lookup
    }
}
