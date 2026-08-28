package com.eliasgonzalez.expensetracker.ui.tray

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.Category
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.ui.common.CategoryAvatar
import com.eliasgonzalez.expensetracker.ui.common.EXPENSES_PAGE_SIZE
import com.eliasgonzalez.expensetracker.ui.common.EmptyState
import com.eliasgonzalez.expensetracker.domain.sanitizeAmountInput
import com.eliasgonzalez.expensetracker.ui.theme.brandColor
import kotlinx.coroutines.launch

@Composable
internal fun TrayScreen() {
    val candidates by ServiceLocator.get().observeCandidates().collectAsState()
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
        items(pending.take(visibleCount), key = { it.id }) { candidate -> PendingCard(candidate) }

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
    var isEditing by rememberSaveable(candidate.id) { mutableStateOf(false) }

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
            Text(
                candidate.merchant,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    var amountText by rememberSaveable { mutableStateOf(candidate.amount.toString()) }
    var merchantText by rememberSaveable { mutableStateOf(candidate.merchant) }
    var categoryId by rememberSaveable { mutableStateOf(candidate.categorySuggestion) }
    val category = Category.fromId(categoryId)

    Text("Editar gasto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    OutlinedTextField(
        value = amountText,
        onValueChange = { amountText = sanitizeAmountInput(it) },
        label = { Text("Monto (₲)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                onSave(amount, merchantText, category)
            },
            modifier = Modifier.weight(1f),
        ) { Text("Guardar") }
    }
}
