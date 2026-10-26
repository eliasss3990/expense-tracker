package com.eliasgonzalez.expensetracker.domain.catalog

import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry

/**
 * Entrada con la que MOSTRAR una categoria por id. Incluye las
 * desactivadas: desactivar solo oculta la categoria de los selectores, los
 * gastos que ya la tienen siguen mostrando su nombre y color. Un id que
 * no esta en el catalogo (borrado por una carrera, o catalogo aun sin
 * cargar) se muestra como "Otro" en vez de romper la pantalla.
 */
fun List<CatalogEntry>.categoryFor(id: String): CatalogEntry =
    firstOrNull { it.id == id }
        ?: firstOrNull { it.protected }
        ?: CatalogDefaults.categories.first { it.protected }
