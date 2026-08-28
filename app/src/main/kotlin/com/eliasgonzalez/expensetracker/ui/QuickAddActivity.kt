package com.eliasgonzalez.expensetracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.model.CandidateStatus
import com.eliasgonzalez.expensetracker.domain.model.Category
import com.eliasgonzalez.expensetracker.domain.model.Expense
import com.eliasgonzalez.expensetracker.domain.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.domain.model.ExpenseSource
import com.eliasgonzalez.expensetracker.ui.theme.ExpenseTrackerTheme
import com.eliasgonzalez.expensetracker.ui.theme.brandColor
import kotlinx.coroutines.launch

const val EXTRA_EDIT_CANDIDATE_ID = "edit_candidate_id"

/**
 * Pantalla única para dos casos de uso (mismo RegisterExpense por debajo):
 * - registro rápido desde el Quick Settings Tile (source = QUICK_TILE)
 * - edición de un candidato detectado antes de confirmarlo
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val editId = intent.getLongExtra(EXTRA_EDIT_CANDIDATE_ID, -1).takeIf { it >= 0 }
        val editingCandidate = editId?.let { ServiceLocator.get().candidateRepository.findById(it) }
            ?.takeIf { it.status == CandidateStatus.PENDING }

        setContent {
            ExpenseTrackerTheme {
                QuickAddScreen(
                    editing = editingCandidate,
                    onSave = { finish() },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@Composable
private fun QuickAddScreen(
    editing: ExpenseCandidate?,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var amountText by remember { mutableStateOf(editing?.amount?.toString().orEmpty()) }
    var merchantText by remember { mutableStateOf(editing?.merchant.orEmpty()) }
    var category by remember {
        mutableStateOf(Category.fromId(editing?.categorySuggestion ?: Category.OTHER.id))
    }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (editing != null) "Editar gasto" else "Nuevo gasto",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = sanitizeAmountInput(it) },
                    label = { Text("Monto (₲)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            val amount = amountText.toLongOrNull() ?: 0
                            if (amount <= 0 || merchantText.isBlank()) return@Button
                            scope.launch {
                                val container = ServiceLocator.get()
                                if (editing != null) {
                                    container.editCandidate(editing.id, amount, merchantText, category.id)
                                } else {
                                    val now = System.currentTimeMillis()
                                    container.registerExpense(
                                        Expense(
                                            amount = amount,
                                            merchant = merchantText,
                                            categoryId = category.id,
                                            occurredAt = now,
                                            createdAt = now,
                                            source = ExpenseSource.QUICK_TILE,
                                        )
                                    )
                                }
                                onSave()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Guardar") }
                }
            }
        }
    }
}
