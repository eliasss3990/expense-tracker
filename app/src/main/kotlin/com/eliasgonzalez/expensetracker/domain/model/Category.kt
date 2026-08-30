package com.eliasgonzalez.expensetracker.domain.model

/**
 * Categorias fijas para esta etapa.
 * Reglas/aliases/aprendizaje de usuario quedan para una fase posterior -
 * por ahora alcanza con una lista cerrada y una categoria por defecto.
 */
enum class Category(val id: String, val label: String) {
    FOOD("FOOD", "Comida"),
    FUEL("FUEL", "Combustible"),
    GROCERIES("GROCERIES", "Supermercado"),
    ENTERTAINMENT("ENTERTAINMENT", "Entretenimiento"),
    SUBSCRIPTIONS("SUBSCRIPTIONS", "Suscripciones"),
    TRANSPORT("TRANSPORT", "Transporte"),
    SHOPPING("SHOPPING", "Compras"),
    HEALTH("HEALTH", "Salud"),
    EDUCATION("EDUCATION", "Educación"),
    MECHANIC("MECHANIC", "Mecánico"),
    OTHER("OTHER", "Otro");

    companion object {
        fun fromId(id: String): Category = entries.find { it.id == id } ?: OTHER
    }
}
