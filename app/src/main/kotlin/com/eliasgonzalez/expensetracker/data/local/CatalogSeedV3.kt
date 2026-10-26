package com.eliasgonzalez.expensetracker.data.local

import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind

/**
 * Lista CONGELADA de lo que sembro la migracion v2->v3. Es literal a
 * proposito y NO se deriva de CatalogDefaults: ese cambia con el codigo
 * (se agrega una categoria, se renombra una), y
 * una migracion ya publicada tiene que producir siempre el mismo resultado
 * sin importar con que version de la app se haga el upgrade. Si
 * CatalogDefaults cambia, CatalogSeedV3Test falla y obliga a decidir si hace
 * falta una migracion nueva con su propia lista.
 */
internal object CatalogSeedV3 {
    val categories: List<CatalogEntry> = listOf(
        entry("FOOD", "Comida", "#F59E0B", 0),
        entry("FUEL", "Combustible", "#EF4444", 1),
        entry("GROCERIES", "Supermercado", "#10B981", 2),
        entry("ENTERTAINMENT", "Entretenimiento", "#8B5CF6", 3),
        entry("SUBSCRIPTIONS", "Suscripciones", "#EC4899", 4),
        entry("TRANSPORT", "Transporte", "#3B82F6", 5),
        entry("SHOPPING", "Compras", "#F97316", 6),
        entry("HEALTH", "Salud", "#14B8A6", 7),
        entry("EDUCATION", "Educación", "#0EA5E9", 8),
        entry("MECHANIC", "Mecánico", "#78716C", 9),
        entry("OTHER", "Otro", "#6B7280", 10, protected = true),
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
