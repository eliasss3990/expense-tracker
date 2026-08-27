package com.eliasgonzalez.expensetracker.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.eliasgonzalez.expensetracker.data.CandidateStore
import com.eliasgonzalez.expensetracker.model.CandidateStatus
import com.eliasgonzalez.expensetracker.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.notification.CandidateActionReceiver
import com.eliasgonzalez.expensetracker.notification.ACTION_ACCEPT
import com.eliasgonzalez.expensetracker.notification.ACTION_REJECT
import com.eliasgonzalez.expensetracker.notification.EXTRA_CANDIDATE_ID
import com.eliasgonzalez.expensetracker.notification.isNotificationListenerEnabled

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    var tab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // POST_NOTIFICATIONS (API 33+) sí se puede pedir con el dialogo estandar.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* el resultado no cambia el flujo, solo queda otorgado o no */ }

    val listenerEnabled = remember { mutableStateOf(isNotificationListenerEnabled(context)) }

    DisposableEffect(lifecycleOwner) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            // Al volver de Ajustes (donde el usuario habilita el acceso a
            // notificaciones a mano), re-chequear el estado.
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled.value = isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold { padding ->
        Column(Modifier.padding(padding)) {
            if (!listenerEnabled.value) {
                NotificationAccessBanner()
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Dashboard") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Bandeja") })
            }
            when (tab) {
                0 -> DashboardScreen()
                1 -> TrayScreen()
            }
        }
    }
}

@Composable
private fun NotificationAccessBanner() {
    val context = LocalContext.current
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Falta activar el acceso a notificaciones",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "Sin este permiso la app no puede detectar gastos automáticamente.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text("Activar ahora") }
        }
    }
}

@Composable
private fun DashboardScreen() {
    val confirmed = CandidateStore.confirmed()
    val total = confirmed.sumOf { it.amount }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AGOSTO 2026", style = MaterialTheme.typography.labelMedium)
        Text("Gastado", style = MaterialTheme.typography.bodyMedium)
        Text("₲%,d".format(total), style = MaterialTheme.typography.headlineMedium)
        Text("Últimos gastos", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(confirmed) { candidate -> ExpenseRow(candidate) }
        }
    }
}

@Composable
private fun TrayScreen() {
    val pending = CandidateStore.pending()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("🔔 ${pending.size} pendientes", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(pending) { candidate -> PendingRow(candidate) }
        }
    }
}

@Composable
private fun ExpenseRow(candidate: ExpenseCandidate) {
    Card(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(candidate.merchant, style = MaterialTheme.typography.titleSmall)
            Text("₲%,d".format(candidate.amount), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PendingRow(candidate: ExpenseCandidate) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(candidate.merchant, style = MaterialTheme.typography.titleSmall)
            Text("₲%,d — ${candidate.sourceApp.orEmpty()}".format(candidate.amount))
            Row {
                Button(onClick = {
                    context.sendBroadcast(
                        Intent(context, CandidateActionReceiver::class.java)
                            .setAction(ACTION_ACCEPT)
                            .putExtra(EXTRA_CANDIDATE_ID, candidate.id)
                    )
                }) { Text("Aceptar") }
                Button(onClick = {
                    val intent = Intent(context, QuickAddActivity::class.java)
                    intent.putExtra(EXTRA_EDIT_CANDIDATE_ID, candidate.id)
                    context.startActivity(intent)
                }) { Text("Editar") }
                Button(onClick = {
                    context.sendBroadcast(
                        Intent(context, CandidateActionReceiver::class.java)
                            .setAction(ACTION_REJECT)
                            .putExtra(EXTRA_CANDIDATE_ID, candidate.id)
                    )
                }) { Text("Rechazar") }
            }
        }
    }
}

@Composable
private fun Row(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
