package com.eliasgonzalez.expensetracker.domain.catalog

import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.model.CategoryIds

/**
 * Entradas de fabrica de las categorias: se siembran en una instalacion nueva
 * (DbHelper.onCreate) y son el respaldo de la UI mientras el catalogo todavia
 * no cargo. Es una lista literal; los ids vienen de [CategoryIds].
 *
 * "Otro" es protegida (no se desactiva ni elimina) y es el comodin al que cae un
 * gasto cuyo id ya no existe en el catalogo. Una categoria DESACTIVADA no cae
 * ahi: se respeta tal cual (ver CategoryPolicy).
 *
 * La migracion v2->v3 NO usa este objeto sino una copia congelada
 * (data/local/CatalogSeedV3): este cambia con el codigo y una migracion
 * publicada tiene que dar siempre el mismo resultado.
 */
object CatalogDefaults {

    val categories: List<CatalogEntry> = listOf(
        entry(CategoryIds.FOOD, "Comida", "#F59E0B", 0),
        entry(CategoryIds.FUEL, "Combustible", "#EF4444", 1),
        entry(CategoryIds.GROCERIES, "Supermercado", "#10B981", 2),
        entry(CategoryIds.ENTERTAINMENT, "Entretenimiento", "#8B5CF6", 3),
        entry(CategoryIds.SUBSCRIPTIONS, "Suscripciones", "#EC4899", 4),
        entry(CategoryIds.TRANSPORT, "Transporte", "#3B82F6", 5),
        entry(CategoryIds.SHOPPING, "Compras", "#F97316", 6),
        entry(CategoryIds.HEALTH, "Salud", "#14B8A6", 7),
        entry(CategoryIds.EDUCATION, "Educación", "#0EA5E9", 8),
        entry(CategoryIds.MECHANIC, "Mecánico", "#78716C", 9),
        entry(CategoryIds.OTHER, "Otro", "#6B7280", 10, protected = true),
    )

    private fun entry(id: String, label: String, color: String, sortOrder: Int, protected: Boolean = false) =
        CatalogEntry(
            kind = CatalogKind.CATEGORY,
            id = id,
            label = label,
            color = color,
            sortOrder = sortOrder,
            builtIn = true,
            protected = protected,
        )
}
