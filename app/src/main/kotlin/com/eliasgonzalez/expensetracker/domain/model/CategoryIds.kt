package com.eliasgonzalez.expensetracker.domain.model

/**
 * Ids de las categorias de fabrica, tal como estan GUARDADOS en la base
 * (`category_id` de expenses, `category_suggestion` de candidates y `id` de
 * catalog_entries). Son los valores que sobrevivieron al enum `Category`,
 * que ya no existe: **nunca se cambian ni se reutilizan**, porque hay datos
 * reales apuntando a ellos (CategoryIdsTest los fija contra su texto literal).
 *
 * El comodin al que cae un id inexistente es `OTHER`, pero el codigo que decide
 * "a cual caer" busca la entrada marcada `protected` del catalogo y solo usa
 * esta constante de respaldo si el catalogo no tiene ninguna.
 */
object CategoryIds {
    const val FOOD = "FOOD"
    const val FUEL = "FUEL"
    const val GROCERIES = "GROCERIES"
    const val ENTERTAINMENT = "ENTERTAINMENT"
    const val SUBSCRIPTIONS = "SUBSCRIPTIONS"
    const val TRANSPORT = "TRANSPORT"
    const val SHOPPING = "SHOPPING"
    const val HEALTH = "HEALTH"
    const val EDUCATION = "EDUCATION"
    const val MECHANIC = "MECHANIC"
    const val OTHER = "OTHER"

    /** Ids de fabrica en el orden en que se siembran. */
    val builtIn: List<String> = listOf(
        FOOD, FUEL, GROCERIES, ENTERTAINMENT, SUBSCRIPTIONS, TRANSPORT,
        SHOPPING, HEALTH, EDUCATION, MECHANIC, OTHER,
    )
}
