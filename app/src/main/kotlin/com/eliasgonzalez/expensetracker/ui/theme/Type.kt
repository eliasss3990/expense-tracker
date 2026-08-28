package com.eliasgonzalez.expensetracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ExpenseTrackerTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
        labelMedium = base.labelMedium.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        ),
    )
}

/** Cifras monetarias grandes en el Dashboard: mono-espaciado ayuda a que
 * los números se lean como números, no como texto corrido. */
val MoneyDisplayStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    letterSpacing = (-0.5).sp,
)
