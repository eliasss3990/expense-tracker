package com.eliasgonzalez.expensetracker.domain.analysis

data class ParserSnapshot(
    val amount: Long,
    val currency: String,
    val merchant: String,
    val merchantConfident: Boolean,
    val parserId: String,
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
