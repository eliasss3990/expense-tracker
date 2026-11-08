package com.eliasgonzalez.expensetracker.ui.ai

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisMode
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionAudit
import com.eliasgonzalez.expensetracker.domain.analysis.DecisionEffect
import com.eliasgonzalez.expensetracker.domain.analysis.ModelInstallState
import com.eliasgonzalez.expensetracker.domain.analysis.ModelInstallStatus
import com.eliasgonzalez.expensetracker.domain.usecase.RecoverResult
import com.eliasgonzalez.expensetracker.ui.theme.rememberCategoryLookup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")

/**
 * Pantalla de la IA local: instalar el modelo, elegir el modo y ver POR QUE cada aviso termino como termino.
 * Los textos viven en AiPresentation (probados); aca solo hay disposicion y llamadas a los casos de uso.
 */
@Composable
internal fun AiScreen() {
    val container = remember { ServiceLocator.get() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val models = container.localModelManager
    val modeStore = container.analysisModeStore

    var modelState by remember { mutableStateOf(models.state()) }
    LaunchedEffect(Unit) {
        // Sondea el estado mientras la pantalla esta abierta; al terminar la descarga la verifica una vez.
        while (true) {
            modelState = models.state()
            if (modelState.status == ModelInstallStatus.DOWNLOADED) {
                modelState = ModelInstallState(ModelInstallStatus.VERIFYING)
                modelState = models.refresh()
            }
            delay(2_000)
        }
    }

    var mode by remember { mutableStateOf(modeStore.currentMode()) }
    var confirmAutomatic by remember { mutableStateOf(false) }
    var decisions by remember { mutableStateOf<List<DecisionAudit>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { decisions = container.listRecentDecisions(50) }
    val categoryLabel = rememberCategoryLookup()

    fun pick(target: AnalysisMode) {
        if (target != AnalysisMode.SHADOW && modelState.status != ModelInstallStatus.INSTALLED) {
            Toast.makeText(context, "Instalá el modelo primero", Toast.LENGTH_SHORT).show()
            return
        }
        if (target == AnalysisMode.AUTOMATIC) confirmAutomatic = true else {
            modeStore.setMode(target)
            mode = target
        }
    }

    if (confirmAutomatic) {
        AlertDialog(
            onDismissRequest = { confirmAutomatic = false },
            title = { Text("Registrar gastos sin preguntarte") },
            text = {
                Text(
                    "En modo Automático, los gastos que el modelo da por seguros se registran solos, sin que los confirmes. " +
                        "Lo dudoso sigue llegando como candidato y todo queda anotado abajo, con el motivo. " +
                        "Conviene probar antes un tiempo en Sombra y Asistido.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    modeStore.setMode(AnalysisMode.AUTOMATIC); mode = AnalysisMode.AUTOMATIC; confirmAutomatic = false
                }) { Text("Activar") }
            },
            dismissButton = { TextButton(onClick = { confirmAutomatic = false }) { Text("Cancelar") } },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Modelo local", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${models.displayName} · ${models.approximateSize} · corre en tu teléfono, nada sale de él.", style = MaterialTheme.typography.bodySmall)
                    Text(AiPresentation.installLabel(modelState))
                    modelState.progressPercent?.takeIf { modelState.status == ModelInstallStatus.DOWNLOADING }?.let {
                        LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            AiPresentation.isBusy(modelState) ->
                                OutlinedButton(onClick = { models.cancel(); modelState = models.state() }) { Text("Cancelar") }
                            modelState.status == ModelInstallStatus.INSTALLED ->
                                OutlinedButton(onClick = {
                                    models.cancel(); modelState = models.state()
                                    if (mode != AnalysisMode.SHADOW) { modeStore.setMode(AnalysisMode.SHADOW); mode = AnalysisMode.SHADOW }
                                }) { Text("Quitar modelo") }
                            else -> Button(onClick = {
                                runCatching { models.startDownload() }
                                    .onFailure { Toast.makeText(context, it.message ?: "No se pudo iniciar la descarga", Toast.LENGTH_LONG).show() }
                                modelState = models.state()
                            }) { Text("Descargar (solo Wi-Fi)") }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Modo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    AnalysisMode.entries.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { pick(m) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            RadioButton(selected = m == mode, onClick = { pick(m) })
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(AiPresentation.modeTitle(m), fontWeight = FontWeight.Medium)
                                Text(AiPresentation.modeDescription(m), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Decisiones recientes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { reload++ }) { Text("Actualizar") }
            }
        }
        if (decisions.isEmpty()) {
            item { Text("Todavía no hay decisiones. Aparecen acá cuando llega un aviso con el modelo instalado.", style = MaterialTheme.typography.bodySmall) }
        }
        items(decisions, key = { it.eventKey + it.decidedAt }) { audit ->
            DecisionCard(audit, categoryLabel, onRecover = {
                scope.launch {
                    val message = when (container.recoverDiscarded(audit.eventKey)) {
                        is RecoverResult.Recovered -> "Recuperado: lo ves en la Bandeja"
                        RecoverResult.Duplicate -> "Ya había un candidato igual"
                        RecoverResult.NotRecoverable -> "No hay datos suficientes para recuperarlo"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    reload++
                }
            })
        }
    }
}

@Composable
private fun DecisionCard(audit: DecisionAudit, categoryLabel: (String) -> com.eliasgonzalez.expensetracker.ui.theme.CategoryUi, onRecover: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(AiPresentation.effectLabel(audit.effect), fontWeight = FontWeight.Medium)
                Text(
                    Instant.ofEpochMilli(audit.decidedAt).atZone(ZoneId.systemDefault()).format(timeFormatter),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("${audit.sourceApp} · ${audit.mode.let(AiPresentation::modeTitle)}", style = MaterialTheme.typography.bodySmall)
            if (expanded) {
                val label = audit.categoryId?.let { categoryLabel(it).label }
                AiPresentation.explain(audit, label).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (audit.effect == DecisionEffect.DISCARDED) {
                    OutlinedButton(onClick = onRecover) { Text("Recuperar") }
                }
            } else {
                Text("Tocá para ver por qué", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
