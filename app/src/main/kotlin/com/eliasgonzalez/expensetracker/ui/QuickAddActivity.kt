package com.eliasgonzalez.expensetracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eliasgonzalez.expensetracker.data.CandidateStore
import com.eliasgonzalez.expensetracker.model.CandidateStatus
import com.eliasgonzalez.expensetracker.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.model.Source

const val EXTRA_EDIT_CANDIDATE_ID = "edit_candidate_id"

/**
 * Pantalla única para dos casos de uso (mismo RegisterExpense conceptual):
 * - registro rápido desde el Quick Settings Tile (source = QUICK_TILE)
 * - edición de un candidato detectado antes de confirmarlo (status = EDITED)
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val editId = intent.getLongExtra(EXTRA_EDIT_CANDIDATE_ID, -1).takeIf { it >= 0 }
        val editingCandidate = editId?.let { CandidateStore.findById(it) }

        setContent {
            MaterialTheme {
                Surface {
                    QuickAddScreen(
                        editing = editingCandidate,
                        onSave = { amount, merchant ->
                            if (editingCandidate != null) {
                                editingCandidate.amount = amount
                                editingCandidate.merchant = merchant
                                editingCandidate.status = CandidateStatus.EDITED
                            } else {
                                CandidateStore.add(
                                    ExpenseCandidate(
                                        id = CandidateStore.newId(),
                                        amount = amount,
                                        merchant = merchant,
                                        source = Source.QUICK_TILE,
                                        detectedAt = System.currentTimeMillis(),
                                        status = CandidateStatus.ACCEPTED,
                                    )
                                )
                            }
                            finish()
                        },
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun QuickAddScreen(
    editing: ExpenseCandidate?,
    onSave: (amount: Long, merchant: String) -> Unit,
) {
    var amountText by remember { mutableStateOf(editing?.amount?.toString().orEmpty()) }
    var merchantText by remember { mutableStateOf(editing?.merchant.orEmpty()) }

    Column(Modifier.padding(24.dp)) {
        Text(if (editing != null) "Editar gasto" else "Nuevo gasto")
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter(Char::isDigit) },
            label = { Text("Monto (₲)") },
            modifier = Modifier.padding(top = 12.dp),
        )
        OutlinedTextField(
            value = merchantText,
            onValueChange = { merchantText = it },
            label = { Text("Comercio") },
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(
            onClick = {
                val amount = amountText.toLongOrNull() ?: 0
                if (amount > 0 && merchantText.isNotBlank()) onSave(amount, merchantText)
            },
            modifier = Modifier.padding(top = 16.dp),
        ) { Text("Guardar") }
    }
}
