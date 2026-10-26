package com.eliasgonzalez.expensetracker.ui.catalog

import com.eliasgonzalez.expensetracker.domain.usecase.CatalogRejection

/**
 * Texto para mostrar cuando una operación del catálogo se rechaza. El
 * `when` es exhaustivo a propósito: agregar un motivo nuevo a
 * CatalogRejection no compila hasta que acá tenga su mensaje.
 */
fun categoryRejectionMessage(reason: CatalogRejection): String = when (reason) {
    CatalogRejection.INVALID_LABEL -> "El nombre no puede estar vacío ni pasar de 40 caracteres."
    CatalogRejection.DUPLICATE_LABEL -> "Ya existe una categoría con ese nombre."
    CatalogRejection.INVALID_COLOR -> "Elegí un color de la paleta."
    CatalogRejection.NOT_FOUND -> "Esa categoría ya no existe."
    CatalogRejection.PROTECTED -> "Esta categoría no se puede desactivar ni eliminar."
    CatalogRejection.IN_USE -> "Tiene gastos o candidatos pendientes asociados: solo se puede desactivar."
    CatalogRejection.NOT_READY -> "Todavía se están cargando los datos, probá de nuevo en un momento."
    CatalogRejection.INVALID_KIND -> "Tipo de lista inválido."
}

/** Subtítulo de una fila de la pantalla de categorías, ej. "Inactiva · 3 gastos". */
fun categoryRowSubtitle(active: Boolean, protected: Boolean, expenseCount: Int): String =
    buildList {
        if (!active) add("Inactiva")
        if (protected) add("Siempre activa")
        add(if (expenseCount == 1) "1 gasto" else "$expenseCount gastos")
    }.joinToString(" · ")

/** Por qué una categoría "en uso" no se puede eliminar, para el diálogo. */
fun categoryInUseReason(expenseCount: Int): String = when {
    expenseCount == 1 -> "tiene 1 gasto"
    expenseCount > 1 -> "tiene $expenseCount gastos"
    else -> "la sugieren candidatos pendientes de la Bandeja"
}

/**
 * Inicial para el avatar de una categoria sin ícono. Toma el primer code point,
 * no el primer `Char`: con `take(1)` un nombre que empieza con emoji
 * (par sustituto de UTF-16) quedaba partido por la mitad y se dibujaba roto.
 */
fun categoryInitial(label: String): String {
    val trimmed = label.trim()
    if (trimmed.isEmpty()) return "?"
    return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
}
