package com.eliasgonzalez.expensetracker.domain

private const val MAX_AMOUNT_DIGITS = 15

/**
 * Sanitiza lo que el usuario escribe en un campo de monto: solo dígitos,
 * cortado a [MAX_AMOUNT_DIGITS] - sin este límite un monto gigante (ej.
 * pegado por error) desborda Long al sumar y rompe los totales del
 * Dashboard con notación científica o valores absurdos.
 */
fun sanitizeAmountInput(input: String): String =
    input.filter(Char::isDigit).take(MAX_AMOUNT_DIGITS)
