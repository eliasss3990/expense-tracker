package com.eliasgonzalez.expensetracker.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eliasgonzalez.expensetracker.data.analysis.ModelInstallStatus
import com.eliasgonzalez.expensetracker.data.analysis.ModelInstaller
import com.eliasgonzalez.expensetracker.data.analysis.SharedPreferencesAnalysisModeProvider
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisMode
import com.eliasgonzalez.expensetracker.domain.analysis.AnalysisQueueMetrics
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AiModelScreen() {
    val context = LocalContext.current
    val installer = remember { ModelInstaller(context) }
    val modeProvider = remember { SharedPreferencesAnalysisModeProvider(context) }
    val scope = rememberCoroutineScope()
    var analysisMode by remember { mutableStateOf(modeProvider.currentMode()) }
    var state by remember { mutableStateOf(installer.state()) }
    var showDownloadConfirmation by remember { mutableStateOf(false) }
    var showAutomaticConfirmation by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var metrics by remember { mutableStateOf<AnalysisQueueMetrics?>(null) }

    LaunchedEffect(installer) {
        while (true) {
            val current = installer.state()
            if (current.status == ModelInstallStatus.DOWNLOADED || current.status == ModelInstallStatus.VERIFYING) {
                state = current.copy(status = ModelInstallStatus.VERIFYING)
                val valid = installer.verifyDownloadedModel()
                if (valid) ServiceLocator.get().retryFailedAnalyses()
                errorMessage = if (valid) null else {
                    "La verificacion SHA-256 fallo. Se elimino el archivo descargado; podes volver a intentarlo."
                }
            }
            state = installer.state()
            delay(1_000)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            metrics = runCatching {
                ServiceLocator.get().notificationAnalysisMetrics()
            }.getOrNull()
            delay(5_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("IA local", style = MaterialTheme.typography.headlineSmall)
        Text("Gemma 4 E2B Instruct", style = MaterialTheme.typography.titleMedium)
        Text(
            "El modelo se ejecuta en el telefono. Las notificaciones se procesan localmente; " +
                "no se envian a un servicio externo de IA."
        )
        Text("Modo de procesamiento", style = MaterialTheme.typography.titleMedium)
        listOf(
            AnalysisMode.SHADOW to "Sombra",
            AnalysisMode.ASSISTED to "Asistido",
            AnalysisMode.AUTOMATIC to "Automático (experimental)",
        ).forEach { (mode, label) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = analysisMode == mode,
                    enabled = mode == AnalysisMode.SHADOW || installer.isInstalled(),
                    onClick = {
                        if (mode == AnalysisMode.AUTOMATIC && analysisMode != AnalysisMode.AUTOMATIC) {
                            showAutomaticConfirmation = true
                        } else {
                            analysisMode = mode
                            modeProvider.setMode(mode)
                        }
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when (mode) {
                            AnalysisMode.SHADOW -> "La IA analiza y guarda métricas, pero se conserva el flujo actual de parsers. No hay auto-registros."
                            AnalysisMode.ASSISTED -> "La IA puede proponer candidatos para revisar, pero nunca confirma gastos automáticamente."
                            AnalysisMode.AUTOMATIC -> "Permite registrar directamente solo cuando parser e IA concuerdan y la evidencia es suficiente."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            "La descarga inicial ocupa aproximadamente ${ModelInstaller.APPROXIMATE_SIZE}. " +
                "Se inicia solo cuando la confirmes y se limita a Wi-Fi para evitar consumir datos moviles."
        )

        when (state.status) {
            ModelInstallStatus.NOT_INSTALLED -> Text("Modelo no instalado.")
            ModelInstallStatus.QUEUED -> Text("Descarga en espera...")
            ModelInstallStatus.DOWNLOADING -> Text(
                state.progressPercent?.let { "Descargando: $it%" } ?: "Descargando modelo..."
            )
            ModelInstallStatus.PAUSED -> Text("Descarga pausada. Puede estar esperando una conexion Wi-Fi.")
            ModelInstallStatus.DOWNLOADED -> Text("Descarga completa. Verificando integridad...")
            ModelInstallStatus.VERIFYING -> Text("Verificando el archivo del modelo...")
            ModelInstallStatus.INSTALLED -> Text("Modelo instalado y verificado.")
            ModelInstallStatus.FAILED -> Text(
                "La descarga fallo (codigo ${state.failureReason ?: "desconocido"})."
            )
        }

        if (state.status in setOf(
                ModelInstallStatus.QUEUED,
                ModelInstallStatus.DOWNLOADING,
                ModelInstallStatus.PAUSED,
            )
        ) {
            LinearProgressIndicator(
                progress = { (state.progressPercent ?: 0) / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${formatBytes(state.downloadedBytes)} / " +
                    if (state.totalBytes > 0L) formatBytes(state.totalBytes) else "tamaño desconocido"
            )
            Button(
                onClick = {
                    installer.cancelDownload()
                    state = installer.state()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancelar descarga")
            }
        }

        if (state.status == ModelInstallStatus.INSTALLED) {
            Text(
                "El motor local ya puede cargar el modelo cuando procese una notificacion elegible.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (state.status !in setOf(
                ModelInstallStatus.QUEUED,
                ModelInstallStatus.DOWNLOADING,
                ModelInstallStatus.PAUSED,
                ModelInstallStatus.DOWNLOADED,
                ModelInstallStatus.VERIFYING,
            )
        ) {
            Button(
                onClick = { showDownloadConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.status == ModelInstallStatus.FAILED) "Reintentar descarga" else "Descargar modelo")
            }
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Text("Métricas de análisis", style = MaterialTheme.typography.titleMedium)
        metrics?.let { data ->
            Text("Predicciones de auto-registro: ${data.autoRegisterSuggested}")
            Text("Requieren revisión: ${data.reviewSuggested}")
            Text("Descartadas como no gasto: ${data.rejected}")
            Text("Fallback legacy: ${data.fallback}")
            Text("Pendientes: ${data.pending} · Procesando: ${data.processing} · Fallidas: ${data.failed}")
            TextButton(
                onClick = { ServiceLocator.get().retryFailedAnalyses() },
                enabled = installer.isInstalled() && data.failed > 0,
            ) {
                Text("Reintentar análisis fallidos")
            }
            Text(
                "Son conteos de decisiones del modelo, no una medición de precisión. " +
                    "La precisión requiere contrastarlas con las confirmaciones, ediciones y rechazos del usuario.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } ?: Text("Cargando métricas...")
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            "Modelo: Gemma 4 E2B Instruct · LiteRT-LM · archivo verificado por SHA-256.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showAutomaticConfirmation) {
        AlertDialog(
            onDismissRequest = { showAutomaticConfirmation = false },
            title = { Text("Activar auto-registro") },
            text = {
                Text(
                    "Los gastos que superen la reconciliacion se guardaran sin pedir confirmacion. " +
                        "El filtro es conservador, pero todavia debe validarse con notificaciones reales. " +
                        "¿Querés activar este modo?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        analysisMode = AnalysisMode.AUTOMATIC
                        modeProvider.setMode(AnalysisMode.AUTOMATIC)
                        showAutomaticConfirmation = false
                    },
                ) { Text("Activar") }
            },
            dismissButton = {
                TextButton(onClick = { showAutomaticConfirmation = false }) { Text("Seguir en sombra") }
            },
        )
    }

    if (showDownloadConfirmation) {
        AlertDialog(
            onDismissRequest = { showDownloadConfirmation = false },
            title = { Text("Descargar modelo de IA") },
            text = {
                Text(
                    "Se descargaran aproximadamente 2.59 GB desde Hugging Face. " +
                        "La descarga solo se permitira por Wi-Fi. ¿Continuar?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDownloadConfirmation = false
                        errorMessage = null
                        scope.launch {
                            runCatching { installer.enqueueDownload() }
                                .onFailure { errorMessage = it.message ?: "No se pudo iniciar la descarga." }
                            state = installer.state()
                        }
                    },
                ) { Text("Descargar") }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadConfirmation = false }) { Text("Cancelar") }
            },
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) "%.2f GB".format(gib) else "%.0f MB".format(bytes / (1024.0 * 1024.0))
}
