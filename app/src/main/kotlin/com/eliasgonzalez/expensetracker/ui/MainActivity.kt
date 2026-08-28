package com.eliasgonzalez.expensetracker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.Category
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.notification.isNotificationListenerEnabled
import com.eliasgonzalez.expensetracker.ui.theme.ExpenseTrackerTheme
import com.eliasgonzalez.expensetracker.ui.theme.MoneyDisplayStyle
import com.eliasgonzalez.expensetracker.ui.theme.brandColor
import com.eliasgonzalez.expensetracker.ui.theme.icon
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class Destination(val label: String) { DASHBOARD("Dashboard"), TRAY("Bandeja"), ACTIVITY("Actividad") }

private const val EXPENSES_PAGE_SIZE = 20

class MainActivity : ComponentActivity() {
    // Estado a nivel Activity (no de Compose) para que onResume() lo pueda
    // actualizar de forma confiable. Usar LocalLifecycleOwner de Compose acá
    // resultó no disparar consistentemente al volver de la pantalla de
    // Ajustes en algunos dispositivos (Samsung OneUI).
    private val listenerEnabledState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExpenseTrackerTheme {
                AppRoot(listenerEnabledState)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        listenerEnabledState.value = isNotificationListenerEnabled(this)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(listenerEnabled: MutableState<Boolean>) {
    var destination by remember { mutableStateOf(Destination.DASHBOARD) }
    val context = LocalContext.current
    val candidates by ServiceLocator.get().candidateRepository.candidates.collectAsState()
    val pendingCount = candidates.count { it.status == CandidateStatus.PENDING }

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
            if (!listenerEnabled.value) {
                NotificationAccessBanner()
            }
            when (destination) {
                Destination.DASHBOARD -> DashboardScreen()
                Destination.TRAY -> TrayScreen()
                Destination.ACTIVITY -> ActivityScreen()
            }
        }
    }
}

@Composable
private fun NotificationAccessBanner() {
    val context = LocalContext.current
    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Falta activar el acceso a notificaciones",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Sin este permiso no se pueden detectar gastos automáticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Text("Activar") }
        }
    }
}

/**
 * Alta manual desde dentro de la app (a diferencia del Quick Settings
 * Tile, que abre su propia ventanita flotante fuera de la app vía
 * QuickAddOverlayService) - mismo RegisterExpense por debajo, la única
 * diferencia real es `source = MANUAL` en vez de `QUICK_TILE`.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ManualAddSheet(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var amountText by remember { mutableStateOf("") }
    var merchantText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.OTHER) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Nuevo gasto", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter(Char::isDigit) },
                label = { Text("Monto (₲)") },
                singleLine = true,
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            )
            OutlinedTextField(
                value = merchantText,
                onValueChange = { merchantText = it },
                label = { Text("Comercio") },
                singleLine = true,
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            )
            Text(
                "Categoría",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Category.entries.forEach { option ->
                    val selected = option == category
                    FilterChip(
                        selected = selected,
                        onClick = { category = option },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = option.brandColor().copy(alpha = 0.18f),
                            selectedLabelColor = option.brandColor(),
                        ),
                    )
                }
            }
            Button(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: 0
                    if (amount <= 0 || merchantText.isBlank()) return@Button
                    scope.launch {
                        val now = System.currentTimeMillis()
                        ServiceLocator.get().registerExpense(
                            Expense(
                                amount = amount,
                                merchant = merchantText,
                                categoryId = category.id,
                                occurredAt = now,
                                createdAt = now,
                                source = ExpenseSource.MANUAL,
                            )
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
            ) { Text("Guardar") }
        }
    }
}

// ---------------------------------------------------------------------
// Dashboard
// ---------------------------------------------------------------------

private fun isInCurrentMonth(epochMillis: Long): Boolean {
    val month = YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
    return month == YearMonth.now()
}

@Composable
private fun DashboardScreen() {
    val scope = rememberCoroutineScope()
    val expenses by ServiceLocator.get().expenseRepository.expenses.collectAsState()
    val sortedExpenses = remember(expenses) { expenses.sortedByDescending { it.createdAt } }
    var visibleCount by remember { mutableStateOf(EXPENSES_PAGE_SIZE) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    val thisMonth = remember(expenses) { expenses.filter { isInCurrentMonth(it.occurredAt) } }
    val total = thisMonth.sumOf { it.amount }
    val average = if (thisMonth.isNotEmpty()) total / thisMonth.size else 0
    val monthLabel = remember {
        YearMonth.now().month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"))
            .replaceFirstChar { it.uppercase() } + " " + YearMonth.now().year
    }
    val byCategory = remember(thisMonth) {
        thisMonth.groupBy { Category.fromId(it.categoryId) }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    if (expenses.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.PieChart,
            title = "Todavía no hay gastos",
            subtitle = "Los que detectes o cargues manualmente van a aparecer acá.",
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    monthLabel.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text("₲%,d".format(total), style = MoneyDisplayStyle)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${thisMonth.size} ${if (thisMonth.size == 1) "movimiento" else "movimientos"} · promedio ₲%,d".format(average),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (byCategory.isNotEmpty()) {
            item {
                SectionCard(title = "Por categoría") {
                    byCategory.forEachIndexed { index, (category, amount) ->
                        CategoryBreakdownRow(category, amount, total)
                        if (index != byCategory.lastIndex) Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectionMode) {
                    Text(
                        "${selectedIds.size} seleccionado${if (selectedIds.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancelar selección")
                        }
                        IconButton(
                            onClick = {
                                val toDelete = selectedIds
                                selectedIds = emptySet()
                                scope.launch { ServiceLocator.get().deleteExpense.many(toDelete) }
                            },
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Eliminar seleccionados",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else {
                    Text("Últimos gastos", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        items(sortedExpenses.take(visibleCount), key = { it.id }) { expense ->
            ExpenseRow(
                expense = expense,
                selectionMode = selectionMode,
                selected = expense.id in selectedIds,
                onToggleSelect = {
                    selectedIds = if (expense.id in selectedIds) {
                        selectedIds - expense.id
                    } else {
                        selectedIds + expense.id
                    }
                },
                onEnterSelection = { selectedIds = setOf(expense.id) },
            )
        }

        if (visibleCount < sortedExpenses.size) {
            item {
                TextButton(
                    onClick = { visibleCount += EXPENSES_PAGE_SIZE },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cargar más") }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownRow(category: Category, amount: Long, totalAmount: Long) {
    val fraction = if (totalAmount > 0) amount.toFloat() / totalAmount else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryAvatar(category, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(category.label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "₲%,d".format(amount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(category.brandColor().copy(alpha = 0.15f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(category.brandColor()),
                )
            }
        }
    }
}

@Composable
private fun CategoryAvatar(category: Category, size: androidx.compose.ui.unit.Dp = 44.dp) {
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

private val dayTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val fullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))

private fun relativeDay(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dayTimeFormatter)
    return when (date) {
        LocalDate.now() -> "Hoy · $time"
        LocalDate.now().minusDays(1) -> "Ayer · $time"
        else -> "${date.format(fullDateFormatter)} · $time"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpenseRow(
    expense: Expense,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isEditing by remember(expense.id) { mutableStateOf(false) }
    var confirmingDelete by remember(expense.id) { mutableStateOf(false) }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("¿Eliminar gasto?") },
            text = { Text("\"${expense.merchant}\" — ₲${"%,d".format(expense.amount)} se va a borrar. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        scope.launch { ServiceLocator.get().deleteExpense(expense.id) }
                    },
                ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancelar") }
            },
        )
    }

    Card(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() },
                onLongClick = { if (!selectionMode) onEnterSelection() },
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            if (isEditing) {
                ExpenseInlineEditForm(
                    expense = expense,
                    onCancel = { isEditing = false },
                    onSave = { amount, merchant, category ->
                        scope.launch {
                            ServiceLocator.get().editExpense(expense.id, amount, merchant, category.id)
                        }
                        isEditing = false
                    },
                )
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (selectionMode) {
                        Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                        Spacer(Modifier.width(4.dp))
                    }
                    CategoryAvatar(Category.fromId(expense.categoryId))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            expense.merchant,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            relativeDay(expense.occurredAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "₲%,d".format(expense.amount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!selectionMode) {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar gasto")
                        }
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Eliminar gasto",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseInlineEditForm(
    expense: Expense,
    onCancel: () -> Unit,
    onSave: (amount: Long, merchant: String, category: Category) -> Unit,
) {
    var amountText by remember { mutableStateOf(expense.amount.toString()) }
    var merchantText by remember { mutableStateOf(expense.merchant) }
    var category by remember { mutableStateOf(Category.fromId(expense.categoryId)) }

    Text("Editar gasto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    OutlinedTextField(
        value = amountText,
        onValueChange = { amountText = it.filter(Char::isDigit) },
        label = { Text("Monto (₲)") },
        singleLine = true,
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
    )
    OutlinedTextField(
        value = merchantText,
        onValueChange = { merchantText = it },
        label = { Text("Comercio") },
        singleLine = true,
        modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
    )
    Row(
        Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Category.entries.forEach { option ->
            val selected = option == category
            FilterChip(
                selected = selected,
                onClick = { category = option },
                label = { Text(option.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = option.brandColor().copy(alpha = 0.18f),
                    selectedLabelColor = option.brandColor(),
                ),
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
        Button(
            onClick = {
                val amount = amountText.toLongOrNull() ?: 0
                if (amount <= 0 || merchantText.isBlank()) return@Button
                onSave(amount, merchantText, category)
            },
            modifier = Modifier.weight(1f),
        ) { Text("Guardar") }
    }
}

// ---------------------------------------------------------------------
// Bandeja
// ---------------------------------------------------------------------

@Composable
private fun TrayScreen() {
    val candidates by ServiceLocator.get().candidateRepository.candidates.collectAsState()
    val pending = remember(candidates) { candidates.filter { it.status == CandidateStatus.PENDING } }
    var visibleCount by remember { mutableStateOf(EXPENSES_PAGE_SIZE) }

    if (pending.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.TaskAlt,
            title = "Todo al día",
            subtitle = "No hay gastos detectados esperando tu revisión.",
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "${pending.size} ${if (pending.size == 1) "pendiente" else "pendientes"}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(pending.take(visibleCount)) { candidate -> PendingCard(candidate) }

        if (visibleCount < pending.size) {
            item {
                TextButton(
                    onClick = { visibleCount += EXPENSES_PAGE_SIZE },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cargar más") }
            }
        }
    }
}

@Composable
private fun PendingCard(candidate: ExpenseCandidate) {
    val scope = rememberCoroutineScope()
    var isEditing by remember(candidate.id) { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (isEditing) {
                InlineEditForm(
                    candidate = candidate,
                    onCancel = { isEditing = false },
                    onSave = { amount, merchant, category ->
                        scope.launch {
                            ServiceLocator.get().editCandidate(candidate.id, amount, merchant, category.id)
                        }
                    },
                )
            } else {
                PendingCardSummary(
                    candidate = candidate,
                    onAccept = { scope.launch { ServiceLocator.get().confirmCandidate(candidate.id) } },
                    onEdit = { isEditing = true },
                    onReject = { scope.launch { ServiceLocator.get().rejectCandidate(candidate.id) } },
                )
            }
        }
    }
}

@Composable
private fun PendingCardSummary(
    candidate: ExpenseCandidate,
    onAccept: () -> Unit,
    onEdit: () -> Unit,
    onReject: () -> Unit,
) {
    val category = Category.fromId(candidate.categorySuggestion)
    Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryAvatar(category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(candidate.merchant, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                candidate.sourceApp.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "₲%,d".format(candidate.amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onAccept,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Aceptar")
        }
        OutlinedButton(
            onClick = onEdit,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Editar")
        }
        OutlinedButton(
            onClick = onReject,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Rechazar")
        }
    }
}

/**
 * Formulario de edición inline, dentro de la misma tarjeta de la Bandeja
 * - antes "Editar" abría QuickAddActivity flotando encima de esta misma
 * tarjeta (que seguía mostrando sus propios Aceptar/Editar/Rechazar
 * detrás), confuso: dos controles superpuestos para el mismo candidato.
 */
@Composable
private fun InlineEditForm(
    candidate: ExpenseCandidate,
    onCancel: () -> Unit,
    onSave: (amount: Long, merchant: String, category: Category) -> Unit,
) {
    var amountText by remember { mutableStateOf(candidate.amount.toString()) }
    var merchantText by remember { mutableStateOf(candidate.merchant) }
    var category by remember { mutableStateOf(Category.fromId(candidate.categorySuggestion)) }

    Text("Editar gasto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    OutlinedTextField(
        value = amountText,
        onValueChange = { amountText = it.filter(Char::isDigit) },
        label = { Text("Monto (₲)") },
        singleLine = true,
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
    )
    OutlinedTextField(
        value = merchantText,
        onValueChange = { merchantText = it },
        label = { Text("Comercio") },
        singleLine = true,
        modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
    )
    Row(
        Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Category.entries.forEach { option ->
            val selected = option == category
            FilterChip(
                selected = selected,
                onClick = { category = option },
                label = { Text(option.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = option.brandColor().copy(alpha = 0.18f),
                    selectedLabelColor = option.brandColor(),
                ),
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
        Button(
            onClick = {
                val amount = amountText.toLongOrNull() ?: 0
                if (amount <= 0 || merchantText.isBlank()) return@Button
                onSave(amount, merchantText, category)
            },
            modifier = Modifier.weight(1f),
        ) { Text("Guardar") }
    }
}

// ---------------------------------------------------------------------
// Actividad
// ---------------------------------------------------------------------

private fun activityIcon(type: ActivityType) = when (type) {
    ActivityType.EXPENSE_CREATED, ActivityType.CANDIDATE_ACCEPTED -> Icons.Filled.CheckCircle
    ActivityType.EXPENSE_EDITED, ActivityType.CANDIDATE_EDITED -> Icons.Filled.Create
    ActivityType.EXPENSE_DELETED -> Icons.Filled.Delete
    ActivityType.CANDIDATE_CREATED -> Icons.Filled.CreditCard
    ActivityType.CANDIDATE_REJECTED -> Icons.Filled.Close
}

private fun activityColor(type: ActivityType) = when (type) {
    ActivityType.EXPENSE_CREATED, ActivityType.CANDIDATE_ACCEPTED -> com.eliasgonzalez.expensetracker.ui.theme.IncomePositive
    ActivityType.CANDIDATE_REJECTED, ActivityType.EXPENSE_DELETED -> com.eliasgonzalez.expensetracker.ui.theme.ExpenseNegative
    else -> com.eliasgonzalez.expensetracker.ui.theme.BrandPrimary
}

private fun activityLabel(type: ActivityType): String = when (type) {
    ActivityType.EXPENSE_CREATED -> "Gasto registrado"
    ActivityType.EXPENSE_EDITED -> "Gasto editado"
    ActivityType.EXPENSE_DELETED -> "Gasto eliminado"
    ActivityType.CANDIDATE_CREATED -> "Gasto detectado"
    ActivityType.CANDIDATE_ACCEPTED -> "Gasto aceptado"
    ActivityType.CANDIDATE_EDITED -> "Gasto editado y aceptado"
    ActivityType.CANDIDATE_REJECTED -> "Candidato rechazado"
}

@Composable
private fun ActivityScreen() {
    val entries by ServiceLocator.get().activityRepository.recent.collectAsState()
    var visibleCount by remember { mutableStateOf(EXPENSES_PAGE_SIZE) }

    if (entries.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.History,
            title = "Sin actividad todavía",
            subtitle = "Acá vas a ver la traza de cada gasto: detectado, aceptado, editado o rechazado.",
        )
        return
    }

    val visibleEntries = remember(entries, visibleCount) { entries.take(visibleCount) }
    val grouped = remember(visibleEntries) {
        visibleEntries.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        grouped.forEach { (date, dayEntries) ->
            item {
                Text(
                    when (date) {
                        LocalDate.now() -> "HOY"
                        LocalDate.now().minusDays(1) -> "AYER"
                        else -> date.format(fullDateFormatter).uppercase()
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            items(dayEntries) { entry -> ActivityRow(entry) }
        }

        if (visibleCount < entries.size) {
            item {
                TextButton(
                    onClick = { visibleCount += EXPENSES_PAGE_SIZE },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cargar más") }
            }
        }
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry) {
    val time = remember(entry.timestamp) {
        Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).format(dayTimeFormatter)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(activityColor(entry.type).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                activityIcon(entry.type),
                contentDescription = null,
                tint = activityColor(entry.type),
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(activityLabel(entry.type), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(entry.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------
// Compartido
// ---------------------------------------------------------------------

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
