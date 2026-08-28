package com.eliasgonzalez.expensetracker.ui.dashboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.model.Category
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.ui.common.RequiredFieldLabel
import com.eliasgonzalez.expensetracker.domain.text.sanitizeAmountInput
import com.eliasgonzalez.expensetracker.ui.theme.brandColor
import kotlinx.coroutines.launch

/**
 * Alta manual desde dentro de la app (a diferencia del Quick Settings
 * Tile, que abre su propia ventanita flotante fuera de la app vía
 * QuickAddOverlayService) - mismo RegisterExpense por debajo, la única
 * diferencia real es `source = MANUAL` en vez de `QUICK_TILE`.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ManualAddSheet(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var amountText by remember { mutableStateOf("") }
    var merchantText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.OTHER) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Nuevo gasto", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = sanitizeAmountInput(it) },
                label = { RequiredFieldLabel("Monto (₲)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            )
            OutlinedTextField(
                value = merchantText,
                onValueChange = { merchantText = it },
                label = { RequiredFieldLabel("Comercio") },
                singleLine = true,
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            )
            OutlinedTextField(
                value = descriptionText,
                onValueChange = { descriptionText = it },
                label = { Text("Descripción") },
                minLines = 2,
                maxLines = 4,
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
                                description = descriptionText.trim(),
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
