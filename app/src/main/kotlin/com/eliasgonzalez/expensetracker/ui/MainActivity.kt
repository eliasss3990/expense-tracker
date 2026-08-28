package com.eliasgonzalez.expensetracker.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.notification.isNotificationListenerEnabled
import com.eliasgonzalez.expensetracker.notification.isPostNotificationsGranted
import com.eliasgonzalez.expensetracker.ui.activitylog.ActivityScreen
import com.eliasgonzalez.expensetracker.ui.dashboard.DashboardScreen
import com.eliasgonzalez.expensetracker.ui.dashboard.ManualAddSheet
import com.eliasgonzalez.expensetracker.ui.onboarding.OnboardingPrefs
import com.eliasgonzalez.expensetracker.ui.onboarding.PermissionsOnboardingScreen
import com.eliasgonzalez.expensetracker.ui.theme.ExpenseTrackerTheme
import com.eliasgonzalez.expensetracker.ui.tray.TrayScreen
import com.eliasgonzalez.expensetracker.update.ReleaseInfo
import com.eliasgonzalez.expensetracker.update.UpdateChecker
import com.eliasgonzalez.expensetracker.update.isNewerVersion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Destination(val label: String) { DASHBOARD("Dashboard"), TRAY("Bandeja"), ACTIVITY("Actividad") }

private const val SPLASH_MIN_DURATION_MS = 2000L

class MainActivity : ComponentActivity() {
    // Estado a nivel Activity (no de Compose) para que onResume() lo pueda
    // actualizar de forma confiable. Usar LocalLifecycleOwner de Compose acá
    // resultó no disparar consistentemente al volver de la pantalla de
    // Ajustes en algunos dispositivos (Samsung OneUI).
    private val listenerEnabledState = mutableStateOf(false)
    private val notificationsGrantedState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Sin esto el splash desaparece apenas se dibuja el primer frame -
        // en un celular rápido dura una fracción de segundo, invisible.
        // Se mantiene un mínimo de tiempo para que la marca realmente se vea.
        var keepSplashOnScreen = true
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }
        lifecycleScope.launch {
            delay(SPLASH_MIN_DURATION_MS)
            keepSplashOnScreen = false
        }

        setContent {
            ExpenseTrackerTheme {
                AppRoot(listenerEnabledState, notificationsGrantedState)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        listenerEnabledState.value = isNotificationListenerEnabled(this)
        notificationsGrantedState.value = isPostNotificationsGranted(this)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(listenerEnabled: MutableState<Boolean>, notificationsGranted: MutableState<Boolean>) {
    var destination by remember { mutableStateOf(Destination.DASHBOARD) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val candidates by ServiceLocator.get().observeCandidates().collectAsState()
    val pendingCount = candidates.count { it.status == CandidateStatus.PENDING }

    val currentVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.0.0"
    }
    var availableUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    suspend fun checkForUpdate(showUpToDateMessage: Boolean) {
        val release = UpdateChecker.fetchLatestRelease()
        when {
            release == null && showUpToDateMessage ->
                Toast.makeText(context, "No se pudo comprobar actualizaciones", Toast.LENGTH_SHORT).show()
            release != null && isNewerVersion(release.versionName, currentVersion) -> {
                availableUpdate = release
                if (showUpToDateMessage) {
                    Toast.makeText(context, "Hay una nueva versión: v${release.versionName}", Toast.LENGTH_SHORT).show()
                }
            }
            showUpToDateMessage ->
                Toast.makeText(context, "Ya tenés la última versión", Toast.LENGTH_SHORT).show()
        }
    }

    // Chequeo silencioso al abrir - si falla (repo privado, sin
    // internet) no molesta con ningún mensaje, solo no muestra el banner.
    LaunchedEffect(Unit) { checkForUpdate(showUpToDateMessage = false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted.value = granted }
    val showNotificationsStep = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val openNotificationListenerSettings = {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    var onboardingCompleted by remember {
        mutableStateOf(OnboardingPrefs.isPermissionsOnboardingCompleted(context))
    }
    if (!onboardingCompleted) {
        PermissionsOnboardingScreen(
            listenerEnabled = listenerEnabled.value,
            notificationsGranted = notificationsGranted.value,
            showNotificationsStep = showNotificationsStep,
            onOpenNotificationListenerSettings = openNotificationListenerSettings,
            onRequestNotificationsPermission = {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onFinish = {
                OnboardingPrefs.markPermissionsOnboardingCompleted(context)
                onboardingCompleted = true
            },
        )
        return
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = ServiceLocator.get().exportBackup()
        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        Toast.makeText(context, "Backup exportado", Toast.LENGTH_SHORT).show()
    }

    var showAddSheet by remember { mutableStateOf(false) }
    if (showAddSheet) {
        ManualAddSheet(onDismiss = { showAddSheet = false })
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo gasto")
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Expense Tracker", fontWeight = FontWeight.Bold) },
                actions = {
                    if (destination == Destination.DASHBOARD) {
                        IconButton(onClick = {
                            val fileName = "expense-tracker-backup-${System.currentTimeMillis()}.json"
                            exportLauncher.launch(fileName)
                        }) {
                            Icon(Icons.Filled.FileDownload, contentDescription = "Exportar backup")
                        }
                    }
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Buscar actualizaciones") },
                            onClick = {
                                showOverflowMenu = false
                                scope.launch { checkForUpdate(showUpToDateMessage = true) }
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = destination == Destination.DASHBOARD,
                    onClick = { destination = Destination.DASHBOARD },
                    icon = { Icon(Icons.Filled.PieChart, contentDescription = null) },
                    label = { Text("Dashboard") },
                )
                NavigationBarItem(
                    selected = destination == Destination.TRAY,
                    onClick = { destination = Destination.TRAY },
                    icon = {
                        BadgedBox(badge = { if (pendingCount > 0) Badge { Text("$pendingCount") } }) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                        }
                    },
                    label = { Text("Bandeja") },
                )
                NavigationBarItem(
                    selected = destination == Destination.ACTIVITY,
                    onClick = { destination = Destination.ACTIVITY },
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    label = { Text("Actividad") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            availableUpdate?.let { release ->
                UpdateAvailableBanner(
                    release = release,
                    onDismiss = { availableUpdate = null },
                )
            }
            MissingPermissionsBanner(
                listenerEnabled = listenerEnabled.value,
                notificationsGranted = notificationsGranted.value,
                showNotificationsStep = showNotificationsStep,
                onOpenNotificationListenerSettings = openNotificationListenerSettings,
                onRequestNotificationsPermission = {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
            when (destination) {
                Destination.DASHBOARD -> DashboardScreen()
                Destination.TRAY -> TrayScreen()
                Destination.ACTIVITY -> ActivityScreen()
            }
        }
    }
}

private data class MissingPermission(
    val title: String,
    val description: String,
    val actionLabel: String,
    val onAction: () -> Unit,
)

/**
 * Lista TODOS los permisos que falten, no solo el acceso a notificaciones
 * - antes había un banner dedicado a un solo permiso, y si el usuario ya
 * lo había resuelto podía seguir faltando el otro sin ningún aviso en el
 * Dashboard (solo se pedía una vez, automático, al abrir la app).
 */
@Composable
private fun MissingPermissionsBanner(
    listenerEnabled: Boolean,
    notificationsGranted: Boolean,
    showNotificationsStep: Boolean,
    onOpenNotificationListenerSettings: () -> Unit,
    onRequestNotificationsPermission: () -> Unit,
) {
    val missing = buildList {
        if (!listenerEnabled) {
            add(
                MissingPermission(
                    title = "Falta activar el acceso a notificaciones",
                    description = "Sin este permiso no se pueden detectar gastos automáticamente.",
                    actionLabel = "Activar",
                    onAction = onOpenNotificationListenerSettings,
                )
            )
        }
        if (showNotificationsStep && !notificationsGranted) {
            add(
                MissingPermission(
                    title = "Falta el permiso de notificaciones",
                    description = "Sin este permiso no vas a ver el aviso de un gasto detectado.",
                    actionLabel = "Permitir",
                    onAction = onRequestNotificationsPermission,
                )
            )
        }
    }
    if (missing.isEmpty()) return

    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column {
            missing.forEachIndexed { index, permission ->
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            permission.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            permission.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    TextButton(onClick = permission.onAction) { Text(permission.actionLabel) }
                }
                if (index != missing.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.15f))
                }
            }
        }
    }
}

@Composable
private fun UpdateAvailableBanner(release: ReleaseInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Hay una nueva versión disponible",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "v${release.versionName} — tocá para descargarla",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            TextButton(onClick = onDismiss) { Text("Ahora no") }
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
            }) { Text("Ver") }
        }
    }
}
