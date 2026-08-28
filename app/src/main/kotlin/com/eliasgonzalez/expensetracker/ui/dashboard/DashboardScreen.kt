package com.eliasgonzalez.expensetracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.model.Category
import com.eliasgonzalez.expensetracker.domain.DateRangeFilter
import com.eliasgonzalez.expensetracker.domain.availableMonths
import com.eliasgonzalez.expensetracker.ui.common.CategoryAvatar
import com.eliasgonzalez.expensetracker.ui.common.EXPENSES_PAGE_SIZE
import com.eliasgonzalez.expensetracker.ui.common.EmptyState
import com.eliasgonzalez.expensetracker.ui.common.SectionCard
import com.eliasgonzalez.expensetracker.domain.matchesDateRange
import com.eliasgonzalez.expensetracker.ui.theme.MoneyDisplayStyle
import com.eliasgonzalez.expensetracker.ui.theme.brandColor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

internal fun isInCurrentMonth(epochMillis: Long): Boolean {
    val month = YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
    return month == YearMonth.now()
}

@Composable
internal fun DashboardScreen() {
    val scope = rememberCoroutineScope()
    val expenses by ServiceLocator.get().observeExpenses().collectAsState()
    val sortedExpenses = remember(expenses) { expenses.sortedByDescending { it.createdAt } }
    var visibleCount by remember { mutableStateOf(EXPENSES_PAGE_SIZE) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    var dateRangeFilter by rememberSaveable { mutableStateOf(DateRangeFilter.ALL) }
    var specificMonth by rememberSaveable { mutableStateOf<YearMonth?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf(setOf<String>()) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val activeFilterCount = categoryFilter.size + if (dateRangeFilter != DateRangeFilter.ALL) 1 else 0
    val filteredExpenses = remember(sortedExpenses, dateRangeFilter, specificMonth, categoryFilter) {
        sortedExpenses.filter {
            matchesDateRange(it, dateRangeFilter, specificMonth) &&
                (categoryFilter.isEmpty() || it.categoryId in categoryFilter)
        }
    }
    LaunchedEffect(dateRangeFilter, specificMonth, categoryFilter) { visibleCount = EXPENSES_PAGE_SIZE }
    val thisMonth = remember(expenses) { expenses.filter { isInCurrentMonth(it.occurredAt) } }
    val total = thisMonth.sumOf { it.amount }
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
                    "${thisMonth.size} ${if (thisMonth.size == 1) "movimiento" else "movimientos"}",
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
                    BadgedBox(
                        badge = {
                            if (activeFilterCount > 0) {
                                Badge { Text(activeFilterCount.toString()) }
                            }
                        },
                    ) {
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = "Filtrar gastos",
                                tint = if (activeFilterCount > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }

        if (filteredExpenses.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Ningún gasto coincide con estos filtros",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { dateRangeFilter = DateRangeFilter.ALL; specificMonth = null; categoryFilter = emptySet() }) {
                        Text("Limpiar filtros")
                    }
                }
            }
        }

        items(filteredExpenses.take(visibleCount), key = { it.id }) { expense ->
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

        if (visibleCount < filteredExpenses.size) {
            item {
                TextButton(
                    onClick = { visibleCount += EXPENSES_PAGE_SIZE },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cargar más") }
            }
        }
    }

    if (showFilterSheet) {
        ExpenseFilterSheet(
            dateRange = dateRangeFilter,
            onDateRangeChange = { dateRangeFilter = it; specificMonth = null },
            specificMonth = specificMonth,
            availableMonths = remember(expenses) { availableMonths(expenses) },
            onSelectMonth = { dateRangeFilter = DateRangeFilter.SPECIFIC_MONTH; specificMonth = it },
            selectedCategories = categoryFilter,
            onToggleCategory = { id ->
                categoryFilter = if (id in categoryFilter) categoryFilter - id else categoryFilter + id
            },
            onClear = { dateRangeFilter = DateRangeFilter.ALL; specificMonth = null; categoryFilter = emptySet() },
            onDismiss = { showFilterSheet = false },
        )
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
