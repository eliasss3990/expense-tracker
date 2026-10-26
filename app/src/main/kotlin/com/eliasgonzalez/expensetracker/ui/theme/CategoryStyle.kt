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
import com.eliasgonzalez.expensetracker.domain.model.Category

/** Ícono de cada categoría de fábrica (las propias del usuario no tienen: ver
 * CategoryUi). El color vive en el catálogo, no acá. */
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
    Category.MECHANIC -> Icons.Filled.Build
    Category.OTHER -> Icons.Filled.QuestionMark
}
