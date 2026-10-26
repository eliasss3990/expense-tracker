package com.eliasgonzalez.expensetracker.domain.catalog

import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.model.Category

/**
 * Entradas de fabrica que se siembran en la DB (instalacion nueva y
 * migracion desde una version sin catalogo). Se derivan del enum
 * `Category`, que sigue siendo la fuente de verdad hasta que la UI migre
 * al catalogo; un test fija que no se desincronicen.
 *
 * Los colores replican `CategoryPalette` (ui/theme/Color.kt) para que
 * al migrar la UI al catalogo nada cambie visualmente. "Otro" es
 * protegida: es el comodin cuando un parser sugiere una categoria que
 * el usuario desactivo.
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
