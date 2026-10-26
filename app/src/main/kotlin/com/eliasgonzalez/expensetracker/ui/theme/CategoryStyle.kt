package com.eliasgonzalez.expensetracker.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.ui.graphics.vector.ImageVector
import com.eliasgonzalez.expensetracker.domain.model.CategoryIds

/**
 * Ícono de las categorías de fábrica, por id. Las propias del usuario no tienen
 * ícono (devuelve null y el avatar muestra la inicial: ver CategoryUi). El color
 * vive en el catálogo, no acá.
 */
fun builtInCategoryIcon(id: String): ImageVector? = when (id) {
    CategoryIds.FOOD -> Icons.Filled.Fastfood
    CategoryIds.FUEL -> Icons.Filled.LocalGasStation
    CategoryIds.GROCERIES -> Icons.Filled.ShoppingCart
    CategoryIds.ENTERTAINMENT -> Icons.Filled.Movie
    CategoryIds.SUBSCRIPTIONS -> Icons.Filled.Subscriptions
    CategoryIds.TRANSPORT -> Icons.Filled.DirectionsBus
    CategoryIds.SHOPPING -> Icons.Filled.ShoppingBag
    CategoryIds.HEALTH -> Icons.Filled.LocalHospital
    CategoryIds.EDUCATION -> Icons.Filled.School
    CategoryIds.MECHANIC -> Icons.Filled.Build
    CategoryIds.OTHER -> Icons.Filled.QuestionMark
    else -> null
}
