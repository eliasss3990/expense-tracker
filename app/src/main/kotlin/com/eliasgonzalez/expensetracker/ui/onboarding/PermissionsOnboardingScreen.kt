package com.eliasgonzalez.expensetracker.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliasgonzalez.expensetracker.ui.common.FullScreenSurface
import com.eliasgonzalez.expensetracker.ui.theme.IncomePositive

/**
 * Pantalla única de permisos al primer abrir la app - antes se pedían
 * salteados (un diálogo automático para POST_NOTIFICATIONS apenas
 * entrabas, y por separado un banner para el acceso a notificaciones),
 * lo que daba la sensación de que la app "nunca terminaba de pedir
 * cosas". Acá se explican y piden los dos juntos, una sola vez; si el
 * usuario los omite, el Dashboard sigue recordándoselo con un banner.
 */
@Composable
fun PermissionsOnboardingScreen(
    listenerEnabled: Boolean,
    notificationsGranted: Boolean,
    showNotificationsStep: Boolean,
    onOpenNotificationListenerSettings: () -> Unit,
    onRequestNotificationsPermission: () -> Unit,
    onFinish: () -> Unit,
) {
    val allGranted = listenerEnabled && (!showNotificationsStep || notificationsGranted)

    FullScreenSurface {
        Column(
            Modifier.fillMaxSize().padding(24.dp).padding(top = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "Antes de empezar",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Expense Tracker necesita estos permisos para detectar tus gastos automáticamente. Podés omitirlos y activarlos después desde el Dashboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))
                PermissionStepCard(
                    title = "Acceso a notificaciones",
                    description = "Deja que la app lea todas las notificaciones para detectar gastos automáticamente.",
                    granted = listenerEnabled,
                    actionLabel = "Activar",
                    onAction = onOpenNotificationListenerSettings,
                )
                if (showNotificationsStep) {
                    Spacer(Modifier.height(12.dp))
                    PermissionStepCard(
                        title = "Avisos de la app",
                        description = "Para que te llegue el aviso cuando se detecta un gasto nuevo, con opción de aceptarlo o editarlo.",
                        granted = notificationsGranted,
                        actionLabel = "Permitir",
                        onAction = onRequestNotificationsPermission,
                    )
                }
            }
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text(if (allGranted) "Continuar" else "Continuar de todos modos")
            }
        }
    }
}

@Composable
private fun PermissionStepCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (granted) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Otorgado", tint = IncomePositive)
            } else {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
