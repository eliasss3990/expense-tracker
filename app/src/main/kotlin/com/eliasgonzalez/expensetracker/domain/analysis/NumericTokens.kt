package com.eliasgonzalez.expensetracker.domain.analysis

import java.text.Normalizer

/**
 * Comprobaciones que NO son semanticas: sirven para detectar que el modelo invento un dato,
 * no para entender la notificacion (eso lo hace el modelo).
 */
internal object NumericTokens {
    private val NUMBER = Regex("""\d(?:[\d.,\s]*\d)?""")

    /** true si [value] aparece como numero en [text], con cualquier separador de miles o decimales. */
    fun contains(text: String, value: Long): Boolean =
        NUMBER.findAll(text).any { token -> variants(token.value).contains(value) }

    private fun variants(token: String): Set<Long> {
        val out = mutableSetOf<Long>()
        // "85 000" puede ser un solo numero o dos: se prueban ambos.
        val parts = token.trim().split(Regex("\\s+"))
        (parts + token.filterNot(Char::isWhitespace)).forEach { part ->
            part.filter(Char::isDigit).toLongOrNull()?.let(out::add)
            // "85.000,00": si el ultimo grupo tras un separador tiene 1-2 digitos son decimales.
            val decimals = Regex("""^(.*\d)[.,](\d{1,2})$""").find(part)
            decimals?.groupValues?.get(1)?.filter(Char::isDigit)?.toLongOrNull()?.let(out::add)
        }
        return out
    }

    private val GUARANI = Regex("""\bGs\b|₲|\bPYG\b|guaran""", RegexOption.IGNORE_CASE)
    private val FOREIGN = Regex(
        """\b(USD|EUR|BRL|ARS)\b|U[$]S|US[$]|€|[$]\s*\d|\bd[oó]lares?\b|\bpesos argentinos\b|\breales\b""",
        RegexOption.IGNORE_CASE,
    )

    /** El texto menciona guaranies (Gs, ₲, PYG). */
    fun mentionsGuarani(text: String): Boolean = GUARANI.containsMatchIn(text)

    /** El texto menciona otra moneda (USD, EUR, $, €...). */
    fun mentionsForeignCurrency(text: String): Boolean = FOREIGN.containsMatchIn(text)

    fun normalizeForMatch(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .filter { it.isLetterOrDigit() }

    /** true si alguna palabra significativa (>= 3 letras) de [merchant] aparece en [text]. */
    fun mentionsAnyWord(text: String, merchant: String): Boolean {
        val haystack = normalizeForMatch(text)
        return merchant.split(Regex("[^\\p{L}\\p{N}]+"))
            .map(::normalizeForMatch)
            .filter { it.length >= 3 }
            .any { it in haystack }
    }
}
