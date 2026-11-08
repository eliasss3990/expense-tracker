package com.eliasgonzalez.expensetracker.domain.analysis

data class ParserSnapshot(
    val amount: Long,
    val currency: String,
    val merchant: String,
    val merchantConfident: Boolean,
    val parserId: String,
    /** Confianza que declara el parser (0.6 a 0.95 segun el parser); se conserva en el candidato. */
    val confidence: Double = 1.0,
    /**
     * true para un parser escrito a mano para UN formato conocido. El parser generico (regex laxa sobre
     * cualquier texto con "Gs. 123") NO es curado: no basta para fiarse del contenido de un buzon.
     */
    val curated: Boolean = true,
)

/**
 * Lo que dijeron los parsers curados a mano. Distingue tres casos que un simple null
 * mezclaba: un parser reconocio la notificacion, un parser la descarto A PROPOSITO
 * (ej. un ingreso: regla curada, confiable) y ningun parser la conocia.
 */
sealed interface ParserEvidence {
    data class Parsed(val snapshot: ParserSnapshot) : ParserEvidence
    data class ExplicitlyIgnored(val parserId: String) : ParserEvidence
    data object NoParser : ParserEvidence
}
