package com.eliasgonzalez.expensetracker.ui.theme

import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.eliasgonzalez.expensetracker.domain.model.Category

/** Ícono + color de marca por categoría - un solo lugar para no repetir
 * este mapeo en cada pantalla que muestra un gasto o candidato. */
fun Category.brandColor(): Color = when (this) {
    Category.FOOD -> CategoryPalette.FOOD
    Category.FUEL -> CategoryPalette.FUEL
    Category.GROCERIES -> CategoryPalette.GROCERIES
    Category.ENTERTAINMENT -> CategoryPalette.ENTERTAINMENT
    Category.SUBSCRIPTIONS -> CategoryPalette.SUBSCRIPTIONS
    Category.TRANSPORT -> CategoryPalette.TRANSPORT
    Category.SHOPPING -> CategoryPalette.SHOPPING
    Category.HEALTH -> CategoryPalette.HEALTH
    Category.EDUCATION -> CategoryPalette.EDUCATION
    Category.OTHER -> CategoryPalette.OTHER
}

fun Category.icon(): ImageVector = when (this) {
    Category.FOOD -> Icons.Filled.Fastfood
    Category.FUEL -> Icons.Filled.LocalGasStation
    Category.GROCERIES -> Icons.Filled.ShoppingCart
    Category.ENTERTAINMENT -> Icons.Filled.Movie
    Category.SUBSCRIPTIONS -> Icons.Filled.Subscriptions
    Category.TRANSPORT -> Icons.Filled.DirectionsBus
    Category.SHOPPING -> Icons.Filled.ShoppingBag
    Category.HEALTH -> Icons.Filled.LocalHospital
    Category.EDUCATION -> Icons.Filled.School
    Category.OTHER -> Icons.Filled.QuestionMark
}
