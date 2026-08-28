package com.eliasgonzalez.expensetracker.ui.banners

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.eliasgonzalez.expensetracker.ui.onboarding.PERMISSION_KEY_NOTIFICATION_LISTENER
import com.eliasgonzalez.expensetracker.ui.onboarding.PERMISSION_KEY_POST_NOTIFICATIONS
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private data class MissingPermission(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val actionLabel: String,
    val onAction: () -> Unit,
)

/**
 * Qué claves de permiso corresponde mostrar, en orden - lógica pura sin
 * Compose, separada del composable para poder testearla sin depender de
 * un dispositivo/emulador (acá no hay infraestructura de tests
 * instrumentados, a propósito).
 */
internal fun missingPermissionKeys(
    listenerEnabled: Boolean,
    notificationsGranted: Boolean,
    showNotificationsStep: Boolean,
    dismissedKeys: Set<String>,
): List<String> = buildList {
    if (!listenerEnabled) add(PERMISSION_KEY_NOTIFICATION_LISTENER)
    if (showNotificationsStep && !notificationsGranted) add(PERMISSION_KEY_POST_NOTIFICATIONS)
}.filter { it !in dismissedKeys }

/**
 * Lista TODOS los permisos que falten, no solo el acceso a notificaciones
 * - antes había un banner dedicado a un solo permiso, y si el usuario ya
 * lo había resuelto podía seguir faltando el otro sin ningún aviso en el
 * Dashboard (solo se pedía una vez, automático, al abrir la app). Cada
 * fila se puede deslizar para ocultar (como una notificación) - sigue
 * disponible desde el menú "Permisos" mientras el permiso siga faltando.
 *
 * Usa el tono `primaryContainer` (el índigo de marca), no `errorContainer`:
 * un permiso pendiente no es un error (no se rompió nada), es un recordatorio
 * - el mismo tono que ya usa UpdateAvailableBanner para avisos no urgentes.
 * El rojo de error queda reservado para cuando algo realmente falla.
 */
@Composable
internal fun MissingPermissionsBanner(
    listenerEnabled: Boolean,
    notificationsGranted: Boolean,
    showNotificationsStep: Boolean,
    dismissedKeys: Set<String>,
    onDismiss: (String) -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onRequestNotificationsPermission: () -> Unit,
) {
    val missing = missingPermissionKeys(listenerEnabled, notificationsGranted, showNotificationsStep, dismissedKeys)
        .mapNotNull { key ->
            when (key) {
                PERMISSION_KEY_NOTIFICATION_LISTENER -> MissingPermission(
                    key = key,
                    icon = Icons.Filled.NotificationsActive,
                    title = "Falta activar el acceso a notificaciones",
                    description = "Sin este permiso no se pueden detectar gastos automáticamente.",
                    actionLabel = "Activar",
                    onAction = onOpenNotificationListenerSettings,
                )
                PERMISSION_KEY_POST_NOTIFICATIONS -> MissingPermission(
                    key = key,
                    icon = Icons.Filled.NotificationsNone,
                    title = "Falta el permiso de notificaciones",
                    description = "Sin este permiso no vas a ver el aviso de un gasto detectado.",
                    actionLabel = "Permitir",
                    onAction = onRequestNotificationsPermission,
                )
                else -> null
            }
        }
    if (missing.isEmpty()) return

    Column {
        // Sin divisor entre filas a propósito: cada una ya es su propia
        // card redondeada con margen vertical, una línea acá se veía
        // como un corte raro cruzando dos tarjetas separadas.
        missing.forEach { permission ->
            DismissibleMissingPermissionRow(permission = permission, onDismissed = { onDismiss(permission.key) })
        }
    }
}

@Composable
private fun DismissibleMissingPermissionRow(permission: MissingPermission, onDismissed: () -> Unit) {
    // Al superar el umbral, la fila no desaparece de golpe (se sentía
    // brusco) - primero colapsa con una animación corta y recién después
    // se avisa al padre para sacarla de la lista de verdad.
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(visible) {
        if (!visible) {
            delay(200)
            onDismissed()
        }
    }
    AnimatedVisibility(
        visible = visible,
        exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150)),
    ) {
        // Arrastre manual en vez de SwipeToDismissBox de Material3: ese
        // componente confirma el valor apenas la posición cruza el umbral
        // DURANTE el arrastre (no al soltar), así que ir y volver del 50%
        // sin soltar el dedo igual lo cerraba a mitad de camino. Acá la
        // decisión de cerrar o volver al lugar se toma una sola vez, en
        // onDragEnd, contra la posición que tenga en ese momento.
        val offsetX = remember { Animatable(0f) }
        var widthPx by remember { mutableFloatStateOf(0f) }
        val scope = rememberCoroutineScope()

        Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            // Fondo revelado mientras se arrastra - la card de encima lo
            // tapa por completo en reposo, se ve al desplazarla al costado.
            // matchParentSize() a propósito, NO fillMaxSize(): este Box no
            // tiene otro hermano que fije la altura del Box padre (la Card
            // es la única referencia), así que fillMaxSize() se expandía a
            // todo el espacio restante de la pantalla en vez de a la
            // altura real de la fila, rompiendo el layout de la fila de
            // abajo.
            Box(
                Modifier.matchParentSize().padding(horizontal = 20.dp),
                contentAlignment = if (offsetX.value >= 0f) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
            Card(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { widthPx = it.width.toFloat() }
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val pastThreshold = widthPx > 0f && abs(offsetX.value) > widthPx * 0.5f
                                scope.launch {
                                    if (pastThreshold) {
                                        val target = if (offsetX.value >= 0f) widthPx else -widthPx
                                        offsetX.animateTo(target, tween(200))
                                        visible = false
                                    } else {
                                        offsetX.animateTo(0f, tween(200))
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { offsetX.animateTo(0f, tween(200)) } },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                            },
                        )
                    },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                // Elevation 0 a propósito, como el resto de las cards inline de la
                // app (ver SectionCard) - el contraste de color ya separa el
                // banner del fondo, una sombra acá se ve fuera de lugar en una
                // fila que además se puede arrastrar.
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Ícono en chip circular sólido (color primary, no primaryContainer)
                    // para que se note el asunto sin depender solo del color de fondo -
                    // el mismo permiso puede verse en dos filas con distinto ícono
                    // (activar acceso vs. permitir notificaciones).
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            permission.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            permission.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            permission.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    TextButton(onClick = permission.onAction) { Text(permission.actionLabel) }
                    IconButton(onClick = { visible = false }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Ocultar aviso",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}
