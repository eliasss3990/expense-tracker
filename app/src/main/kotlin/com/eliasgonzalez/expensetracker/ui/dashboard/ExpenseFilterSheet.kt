package com.eliasgonzalez.expensetracker.ui.dashboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliasgonzalez.expensetracker.domain.model.Category
import com.eliasgonzalez.expensetracker.ui.DateRangeFilter
import com.eliasgonzalez.expensetracker.ui.theme.brandColor
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthChipFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM yyyy", Locale.forLanguageTag("es"))

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ExpenseFilterSheet(
    dateRange: DateRangeFilter,
    onDateRangeChange: (DateRangeFilter) -> Unit,
    specificMonth: YearMonth?,
    availableMonths: List<YearMonth>,
    onSelectMonth: (YearMonth) -> Unit,
    selectedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Filtrar gastos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClear) { Text("Limpiar") }
            }
            Text(
                "Período",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateRangeFilter.entries.filter { it != DateRangeFilter.SPECIFIC_MONTH }.forEach { option ->
                    FilterChip(
                        selected = option == dateRange,
                        onClick = { onDateRangeChange(option) },
                        label = { Text(option.label) },
                    )
                }
            }
            if (availableMonths.isNotEmpty()) {
                Text(
                    "Otro mes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableMonths.forEach { month ->
                        val selected = dateRange == DateRangeFilter.SPECIFIC_MONTH && month == specificMonth
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectMonth(month) },
                            label = { Text(month.format(monthChipFormatter).replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
            Text(
                "Categoría",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Category.entries.forEach { option ->
                    val selected = option.id in selectedCategories
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleCategory(option.id) },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = option.brandColor().copy(alpha = 0.18f),
                            selectedLabelColor = option.brandColor(),
                        ),
                    )
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Text("Aplicar")
            }
        }
    }
}
