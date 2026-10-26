package com.eliasgonzalez.expensetracker.domain.catalog

import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.model.Category

/**
 * Entradas de fabrica de las categorias: se siembran en una instalacion nueva
 * (DbHelper.onCreate). Se derivan del enum `Category`, que hoy aporta solo los
 * ids y los íconos de fabrica; la UI ya lee del catalogo, no del enum.
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

    private val categoryColors = mapOf(
        Category.FOOD to "#F59E0B",
        Category.FUEL to "#EF4444",
        Category.GROCERIES to "#10B981",
        Category.ENTERTAINMENT to "#8B5CF6",
        Category.SUBSCRIPTIONS to "#EC4899",
        Category.TRANSPORT to "#3B82F6",
        Category.SHOPPING to "#F97316",
        Category.HEALTH to "#14B8A6",
        Category.EDUCATION to "#0EA5E9",
        Category.MECHANIC to "#78716C",
        Category.OTHER to "#6B7280",
    )

    val categories: List<CatalogEntry> = Category.entries.mapIndexed { index, category ->
        CatalogEntry(
            kind = CatalogKind.CATEGORY,
            id = category.id,
            label = category.label,
            color = categoryColors.getValue(category),
            sortOrder = index,
            builtIn = true,
            protected = category == Category.OTHER,
        )
    }
}
