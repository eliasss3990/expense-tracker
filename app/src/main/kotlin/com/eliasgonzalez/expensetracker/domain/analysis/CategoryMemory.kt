package com.eliasgonzalez.expensetracker.domain.analysis

import java.text.Normalizer

/**
 * "Este comercio ya lo categorizaste": vota la categoria mas usada por el usuario para un comercio.
 * Es su propia decision pasada, asi que gana sobre cualquier suposicion del modelo. Los votos del
 * comodin no cuentan (un gasto que quedo en "Otro" no es una decision sobre el comercio).
 */
class CategoryMemory(history: List<Pair<String, String>>, private val wildcardId: String) {

    private val votes: Map<String, Map<String, Int>> = history
        .filter { (merchant, category) -> merchant.isNotBlank() && category != wildcardId }
        .groupBy({ key(it.first) }, { it.second })
        .mapValues { (_, categories) -> categories.groupingBy { it }.eachCount() }

    /** La categoria mas votada, o null si no hay historial o hay empate (no se adivina). */
    fun suggest(merchant: String?): String? {
        val counts = votes[key(merchant ?: return null)] ?: return null
        val top = counts.maxOf { it.value }
        return counts.filterValues { it == top }.keys.singleOrNull()
    }

    private fun key(merchant: String): String = normalize(merchant)

    companion object {
        /** Minusculas, sin tildes ni signos: "Café  Central." == "cafe central". */
        fun normalize(value: String): String =
            Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).filter { it.isLetterOrDigit() }
    }
}
