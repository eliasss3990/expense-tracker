package com.eliasgonzalez.expensetracker.domain.analysis

/** De donde salio la categoria de un gasto detectado: se audita para poder medir cada fuente. */
enum class CategorySource {
    /** El usuario ya categorizo antes ese comercio: se repite su decision. */
    MEMORY,

    /** El modelo la eligio entre las categorias activas del catalogo. */
    MODEL,

    /** Nada aplicaba: queda en el comodin del catalogo. */
    DEFAULT,
}

data class CategoryChoice(val categoryId: String, val source: CategorySource)
