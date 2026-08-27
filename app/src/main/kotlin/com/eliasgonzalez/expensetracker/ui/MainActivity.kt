package com.eliasgonzalez.expensetracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eliasgonzalez.expensetracker.data.CandidateStore
import com.eliasgonzalez.expensetracker.model.CandidateStatus
import com.eliasgonzalez.expensetracker.model.ExpenseCandidate
import com.eliasgonzalez.expensetracker.notification.CandidateActionReceiver
import com.eliasgonzalez.expensetracker.notification.ACTION_ACCEPT
import com.eliasgonzalez.expensetracker.notification.ACTION_REJECT
import com.eliasgonzalez.expensetracker.notification.EXTRA_CANDIDATE_ID
import android.content.Intent

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
    Scaffold { padding ->
        Column(Modifier.padding(padding)) {
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
