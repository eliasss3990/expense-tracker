package com.eliasgonzalez.expensetracker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.model.Category
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.notification.isNotificationListenerEnabled
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    // Estado a nivel Activity (no de Compose) para que onResume() lo pueda
    // actualizar de forma confiable. Usar LocalLifecycleOwner de Compose acá
    // resultó no disparar consistentemente al volver de la pantalla de
    // Ajustes en algunos dispositivos (Samsung OneUI).
    private val listenerEnabledState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppRoot(listenerEnabledState)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        listenerEnabledState.value = isNotificationListenerEnabled(this)
    }
}

@Composable
private fun AppRoot(listenerEnabled: MutableState<Boolean>) {
    var tab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    // POST_NOTIFICATIONS (API 33+) sí se puede pedir con el dialogo estandar.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* el resultado no cambia el flujo, solo queda otorgado o no */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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

private fun isInCurrentMonth(epochMillis: Long): Boolean {
    val month = YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
    return month == YearMonth.now()
}

@Composable
private fun DashboardScreen() {
    val expenses by ServiceLocator.get().expenseRepository.expenses.collectAsState()
    val thisMonth = expenses.filter { isInCurrentMonth(it.occurredAt) }
    val total = thisMonth.sumOf { it.amount }
    val monthLabel = remember {
        YearMonth.now().month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es")).uppercase() +
            " " + YearMonth.now().year
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(monthLabel, style = MaterialTheme.typography.labelMedium)
        Text("Gastado", style = MaterialTheme.typography.bodyMedium)
        Text("₲%,d".format(total), style = MaterialTheme.typography.headlineMedium)
        Text("Últimos gastos", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(expenses.take(20)) { expense -> ExpenseRow(expense) }
        }
    }
}

@Composable
private fun TrayScreen() {
    val candidates by ServiceLocator.get().candidateRepository.candidates.collectAsState()
    val pending = candidates.filter {
        it.status == com.eliasgonzalez.expensetracker.domain.model.CandidateStatus.PENDING
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("🔔 ${pending.size} pendientes", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(pending) { candidate -> PendingRow(candidate) }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense) {
    Card(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(expense.merchant, style = MaterialTheme.typography.titleSmall)
            Text(
                "₲%,d — %s".format(expense.amount, Category.fromId(expense.categoryId).label),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PendingRow(candidate: ExpenseCandidate) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(candidate.merchant, style = MaterialTheme.typography.titleSmall)
            Text("₲%,d — ${candidate.sourceApp.orEmpty()}".format(candidate.amount))
            Row {
                Button(onClick = {
                    scope.launch { ServiceLocator.get().confirmCandidate(candidate.id) }
                }) { Text("Aceptar") }
                Button(onClick = {
                    val intent = Intent(context, QuickAddActivity::class.java)
                    intent.putExtra(EXTRA_EDIT_CANDIDATE_ID, candidate.id)
                    context.startActivity(intent)
                }) { Text("Editar") }
                Button(onClick = {
                    scope.launch { ServiceLocator.get().rejectCandidate(candidate.id) }
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
