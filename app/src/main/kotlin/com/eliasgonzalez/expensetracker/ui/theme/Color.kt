package com.eliasgonzalez.expensetracker.ui.theme

import androidx.compose.ui.graphics.Color

// Marca: índigo profundo, transmite confianza sin ser el violeta genérico
// de Material por defecto.
val BrandPrimary = Color(0xFF4338CA)
val BrandPrimaryDark = Color(0xFFA5B4FC)
val BrandPrimaryContainer = Color(0xFFE0E7FF)
val BrandOnPrimaryContainer = Color(0xFF1E1B4B)

val SurfaceLight = Color(0xFFFAFAFC)
val SurfaceDark = Color(0xFF15141B)
val SurfaceVariantLight = Color(0xFFF1F0F7)
val SurfaceVariantDark = Color(0xFF211F2B)

val OutlineLight = Color(0xFFE2E1EC)
val OutlineDark = Color(0xFF2E2C3A)

val ExpenseNegative = Color(0xFFDC2626)
val IncomePositive = Color(0xFF16A34A)

/** Paleta fija por categoría - se usa tanto en light como dark (los
 * fondos "container" se derivan con alpha, no un color aparte por tema). */
object CategoryPalette {
    val FOOD = Color(0xFFF59E0B)
    val FUEL = Color(0xFFEF4444)
    val GROCERIES = Color(0xFF10B981)
    val ENTERTAINMENT = Color(0xFF8B5CF6)
    val SUBSCRIPTIONS = Color(0xFFEC4899)
    val TRANSPORT = Color(0xFF3B82F6)
    val SHOPPING = Color(0xFFF97316)
    val HEALTH = Color(0xFF14B8A6)
    val EDUCATION = Color(0xFF0EA5E9)
    val OTHER = Color(0xFF6B7280)
}
