package com.eliasgonzalez.expensetracker.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.model.Category
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.ui.common.CategoryAvatar
import com.eliasgonzalez.expensetracker.ui.common.RequiredFieldLabel
import com.eliasgonzalez.expensetracker.ui.common.relativeDay
import com.eliasgonzalez.expensetracker.ui.sanitizeAmountInput
import com.eliasgonzalez.expensetracker.ui.theme.brandColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ExpenseRow(
    expense: Expense,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isEditing by rememberSaveable(expense.id) { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable(expense.id) { mutableStateOf(false) }
    var showingDetail by rememberSaveable(expense.id) { mutableStateOf(false) }

    if (showingDetail) {
        ExpenseDetailDialog(expense = expense, onDismiss = { showingDetail = false })
    }

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
                onClick = {
                    when {
                        selectionMode -> onToggleSelect()
                        !isEditing -> showingDetail = true
                    }
                },
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
                    onSave = { amount, merchant, category, description ->
                        scope.launch {
                            ServiceLocator.get().editExpense(expense.id, amount, merchant, category.id, description)
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
    onSave: (amount: Long, merchant: String, category: Category, description: String) -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf(expense.amount.toString()) }
    var merchantText by rememberSaveable { mutableStateOf(expense.merchant) }
    var descriptionText by rememberSaveable { mutableStateOf(expense.description) }
    var categoryId by rememberSaveable { mutableStateOf(expense.categoryId) }
    val category = Category.fromId(categoryId)

    Text("Editar gasto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    OutlinedTextField(
        value = amountText,
        onValueChange = { amountText = sanitizeAmountInput(it) },
        label = { RequiredFieldLabel("Monto (₲)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
    )
    OutlinedTextField(
        value = merchantText,
        onValueChange = { merchantText = it },
        label = { RequiredFieldLabel("Comercio") },
        singleLine = true,
        modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
    )
    OutlinedTextField(
        value = descriptionText,
        onValueChange = { descriptionText = it },
        label = { Text("Descripción") },
        minLines = 2,
        maxLines = 4,
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
                onClick = { categoryId = option.id },
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
                onSave(amount, merchantText, category, descriptionText.trim())
            },
            modifier = Modifier.weight(1f),
        ) { Text("Guardar") }
    }
}

/**
 * Detalle completo de un gasto - la fila de la lista solo muestra comercio,
 * dia relativo y monto (eso no cambia); acá se ve todo lo demás (categoría,
 * fecha y hora exactas, origen y la descripción libre si tiene una).
 */
@Composable
private fun ExpenseDetailDialog(expense: Expense, onDismiss: () -> Unit) {
    val category = Category.fromId(expense.categoryId)
    val dateFormat = remember {
        java.text.SimpleDateFormat("d 'de' MMMM yyyy, HH:mm", java.util.Locale("es", "PY"))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryAvatar(category, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(expense.merchant, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        category.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column {
                Text(
                    "₲%,d".format(expense.amount),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    dateFormat.format(java.util.Date(expense.occurredAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    when (expense.source) {
                        ExpenseSource.MANUAL -> "Cargado manualmente"
                        ExpenseSource.QUICK_TILE -> "Cargado desde acceso rápido"
                        ExpenseSource.NOTIFICATION -> "Detectado desde notificación"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(
                    "Descripción",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    expense.description.ifBlank { "Sin descripción" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}
