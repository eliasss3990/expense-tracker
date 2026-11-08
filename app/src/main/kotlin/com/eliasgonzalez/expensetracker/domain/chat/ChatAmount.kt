package com.eliasgonzalez.expensetracker.domain.chat

/**
 * Importe en guaranies escrito como lo escribe una persona: "5000", "80.000", "1.250.000" (el punto o la coma separan
 * miles). Cualquier otra cosa ("2,5 mil", "1.5", "-5000") NO se interpreta: quitar los caracteres raros cambiaba el numero en silencio.
 */
internal object ChatAmount {
    private val grouped = Regex("""\d{1,3}([.,]\d{3})+""")
    private val plain = Regex("""\d+""")

    fun parse(raw: String): Long? {
        val v = raw.trim().removePrefix("₲").removeSuffix("Gs").removeSuffix("gs").trim()
        if (!grouped.matches(v) && !plain.matches(v)) return null
        return v.filter(Char::isDigit).toLongOrNull()
    }
}
