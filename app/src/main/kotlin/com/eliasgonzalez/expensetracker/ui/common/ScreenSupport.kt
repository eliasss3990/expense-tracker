package com.eliasgonzalez.expensetracker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eliasgonzalez.expensetracker.domain.model.Category
import com.eliasgonzalez.expensetracker.ui.theme.brandColor
import com.eliasgonzalez.expensetracker.ui.theme.icon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Composables y helpers compartidos por Dashboard, Bandeja y Actividad -
 * viven acá (en vez de en cada pantalla) justamente porque cruzan las tres.
 */

internal const val EXPENSES_PAGE_SIZE = 20

internal val dayTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
internal val fullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))

internal fun relativeDay(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dayTimeFormatter)
    return when (date) {
        LocalDate.now() -> "Hoy · $time"
        LocalDate.now().minusDays(1) -> "Ayer · $time"
        else -> "${date.format(fullDateFormatter)} · $time"
    }
}

@Composable
internal fun CategoryAvatar(category: Category, size: Dp = 44.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(category.brandColor().copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            category.icon(),
            contentDescription = category.label,
            tint = category.brandColor(),
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
internal fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
internal fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Label de un campo obligatorio con un asterisco rojo al final. */
@Composable
internal fun RequiredFieldLabel(text: String) {
    Text(
        buildAnnotatedString {
            append(text)
            append(" ")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) { append("*") }
        }
    )
}

/**
 * Envoltorio obligatorio para cualquier pantalla completa que NO pase
 * por Scaffold (como la de onboarding de permisos) - sin esto, la
 * pantalla hereda el fondo claro fijo de la ventana base
 * (`android:windowBackground`) en vez del fondo del tema actual
 * (claro/oscuro vía MaterialTheme.colorScheme.background), y termina
 * desincronizada con los íconos de la barra de estado, que sí siguen el
 * tema (ver ExpenseTrackerTheme). Scaffold ya resuelve esto solo; esto
 * es para las pantallas que no lo usan.
 */
@Composable
internal fun FullScreenSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.statusBarsPadding(), content = content)
    }
}
