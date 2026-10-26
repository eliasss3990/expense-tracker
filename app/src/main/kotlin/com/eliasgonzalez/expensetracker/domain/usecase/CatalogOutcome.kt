package com.eliasgonzalez.expensetracker.domain.usecase

import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry

enum class CatalogRejection {
    INVALID_KIND,
    INVALID_LABEL,
    DUPLICATE_LABEL,
    INVALID_COLOR,
    NOT_FOUND,
    PROTECTED,
    IN_USE,
}

/** Resultado de una operacion de catalogo: la UI decide el mensaje. */
sealed interface CatalogOutcome {
    data class Success(val entry: CatalogEntry? = null) : CatalogOutcome
    data class Rejected(val reason: CatalogRejection) : CatalogOutcome
}

internal const val CATALOG_LABEL_MAX_LENGTH = 40
private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

internal fun String.isValidCatalogColor(): Boolean = HEX_COLOR.matches(this)

private val WHITESPACE = Regex("\\s+")

internal fun String.normalizedCatalogLabel(): String = trim().replace(WHITESPACE, " ")

internal fun String.isValidCatalogLabel(): Boolean =
    isNotBlank() && normalizedCatalogLabel().length <= CATALOG_LABEL_MAX_LENGTH
