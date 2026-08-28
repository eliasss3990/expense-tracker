package com.eliasgonzalez.expensetracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    background = SurfaceLight,
    surface = Color.White,
    surfaceVariant = SurfaceVariantLight,
    outline = OutlineLight,
    error = ExpenseNegative,
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = BrandOnPrimaryContainer,
    primaryContainer = BrandPrimary,
    onPrimaryContainer = BrandPrimaryContainer,
    background = SurfaceDark,
    surface = Color(0xFF1D1B24),
    surfaceVariant = SurfaceVariantDark,
    outline = OutlineDark,
    error = Color(0xFFFF6B6B),
)

@Composable
fun ExpenseTrackerTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colors = if (darkTheme) DarkColors else LightColors

    // Sin esto, los íconos de la barra de estado (hora, batería, señal)
    // quedan del color que haya elegido el sistema por default - en tema
    // claro eso puede ser blanco sobre blanco, invisible.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = ExpenseTrackerTypography,
        content = content,
    )
}
