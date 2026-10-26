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
import com.eliasgonzalez.expensetracker.domain.catalog.categoryFor
import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.model.Category

/**
 * Lo que la UI necesita para dibujar una categoria, armado desde una
 * entrada del catalogo. Reemplaza al enum `Category` en las pantallas:
 * conserva los mismos nombres (`label`, `id`, `brandColor()`) para que el
 * cambio en cada una sea casi mecanico.
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
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(FALLBACK_COLOR)

fun CatalogEntry.toCategoryUi(): CategoryUi = CategoryUi(
    id = id,
    label = label,
    color = parseCatalogColor(color),
    // El ícono sigue atado al id de las categorias de fabrica: renombrarla
    // cambia el texto, no el dibujo.
    icon = Category.entries.find { it.id == id }?.icon(),
    active = active,
)

@Composable
private fun rememberCatalogEntries(): List<CatalogEntry> {
    val flow = remember {
        ServiceLocator.get().observeCatalog(CatalogKind.CATEGORY, onlyActive = false)
    }
    // Hasta la primera emision (catalogo aun cargando) se usan las de fabrica,
    // asi la primera pantalla no sale con la lista vacia.
    val entries by flow.collectAsState(initial = CatalogDefaults.categories)
    return entries
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
 * como "Otro".
 */
@Composable
fun rememberCategoryLookup(): (String) -> CategoryUi {
    val entries = rememberCatalogEntries()
    return remember(entries) { { id -> entries.categoryFor(id).toCategoryUi() } }
}
