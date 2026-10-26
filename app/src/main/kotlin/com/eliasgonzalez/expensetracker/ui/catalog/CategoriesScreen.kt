package com.eliasgonzalez.expensetracker.ui.catalog

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.model.CatalogEntry
import com.eliasgonzalez.expensetracker.domain.model.CatalogKind
import com.eliasgonzalez.expensetracker.domain.usecase.CATALOG_LABEL_MAX_LENGTH
import com.eliasgonzalez.expensetracker.domain.usecase.CatalogOutcome
import com.eliasgonzalez.expensetracker.ui.common.CategoryAvatar
import com.eliasgonzalez.expensetracker.ui.theme.parseCatalogColor
import com.eliasgonzalez.expensetracker.ui.theme.toCategoryUi
import kotlinx.coroutines.launch

private sealed interface EditorState {
    data object Closed : EditorState
    data object Creating : EditorState
    data class Renaming(val entry: CatalogEntry) : EditorState
}

/**
 * Gestión de categorías: crear, renombrar, desactivar/reactivar y eliminar.
 * Cada acción pasa por los casos de uso del catálogo; si una regla la
 * rechaza (nombre repetido, categoría con gastos, la protegida) se muestra
 * el motivo en vez de fallar en silencio.
 *
 * Desactivar solo la saca de los selectores para elegir: los gastos que ya
 * la usan no cambian. Una categoría con gastos no se puede eliminar.
 */
@Composable
internal fun CategoriesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = ServiceLocator.get()
    val kind = CatalogKind.CATEGORY

    val entries by remember { container.observeCatalog(kind, onlyActive = false) }
        .collectAsState(initial = emptyList())
    val expenses by container.observeExpenses().collectAsState()
    val countByCategory = remember(expenses) { expenses.groupingBy { it.categoryId }.eachCount() }

    var editor by remember { mutableStateOf<EditorState>(EditorState.Closed) }
    var pendingDelete by remember { mutableStateOf<CatalogEntry?>(null) }

    fun report(outcome: CatalogOutcome) {
        if (outcome is CatalogOutcome.Rejected) {
            Toast.makeText(context, categoryRejectionMessage(outcome.reason), Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Categorías", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Desactivá las que no uses: dejan de aparecer para elegir, pero tus gastos viejos las conservan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { editor = EditorState.Creating }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Nueva")
                }
            }
        }
        items(entries, key = { it.id }) { entry ->
            CategoryRow(
                entry = entry,
                expenseCount = countByCategory[entry.id] ?: 0,
                onToggleActive = { active ->
                    scope.launch { report(container.setCatalogEntryActive(kind, entry.id, active)) }
                },
                onRename = { editor = EditorState.Renaming(entry) },
                onDelete = { pendingDelete = entry },
            )
        }
    }

    when (val state = editor) {
        EditorState.Closed -> Unit
        EditorState.Creating -> CategoryEditorDialog(
            title = "Nueva categoría",
            initialLabel = "",
            showColors = true,
            confirmText = "Crear",
            onDismiss = { editor = EditorState.Closed },
            onConfirm = { label, color ->
                scope.launch {
                    val outcome = container.createCatalogEntry(kind, label, color)
                    report(outcome)
                    if (outcome is CatalogOutcome.Success) editor = EditorState.Closed
                }
            },
        )
        is EditorState.Renaming -> CategoryEditorDialog(
            title = "Renombrar categoría",
            initialLabel = state.entry.label,
            showColors = false,
            confirmText = "Guardar",
            onDismiss = { editor = EditorState.Closed },
            onConfirm = { label, _ ->
                scope.launch {
                    val outcome = container.renameCatalogEntry(kind, state.entry.id, label)
                    report(outcome)
                    if (outcome is CatalogOutcome.Success) editor = EditorState.Closed
                }
            },
        )
    }

    pendingDelete?.let { entry ->
        val count = countByCategory[entry.id] ?: 0
        if (count > 0) {
            // Con gastos asociados no se elimina: se ofrece desactivar en su lugar.
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("No se puede eliminar") },
                text = {
                    Text(
                        "\"${entry.label}\" tiene $count ${if (count == 1) "gasto" else "gastos"}. " +
                            "Para dejar de usarla, desactivala: tus gastos la conservan y deja de aparecer para elegir.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDelete = null
                            scope.launch { report(container.setCatalogEntryActive(kind, entry.id, false)) }
                        },
                    ) { Text("Desactivar") }
                },
                dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
            )
        } else {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("¿Eliminar categoría?") },
                text = { Text("\"${entry.label}\" se va a borrar. Esta acción no se puede deshacer.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDelete = null
                            scope.launch { report(container.deleteCatalogEntry(kind, entry.id)) }
                        },
                    ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
            )
        }
    }
}

@Composable
private fun CategoryRow(
    entry: CatalogEntry,
    expenseCount: Int,
    onToggleActive: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(entry.toCategoryUi())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (entry.active) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    categoryRowSubtitle(entry.active, entry.protected, expenseCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = entry.active,
                onCheckedChange = onToggleActive,
                enabled = !entry.protected,
            )
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Renombrar ${entry.label}")
            }
            IconButton(onClick = onDelete, enabled = !entry.protected) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar ${entry.label}")
            }
        }
    }
}

@Composable
private fun CategoryEditorDialog(
    title: String,
    initialLabel: String,
    showColors: Boolean,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (label: String, color: String) -> Unit,
) {
    var label by rememberSaveable { mutableStateOf(initialLabel) }
    var color by rememberSaveable { mutableStateOf(CATEGORY_COLOR_CHOICES.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(CATALOG_LABEL_MAX_LENGTH) },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showColors) {
                    Text(
                        "Color",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CATEGORY_COLOR_CHOICES.forEach { hex ->
                            ColorDot(hex = hex, selected = hex == color, onClick = { color = hex })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(label, color) }, enabled = label.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ColorDot(hex: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(parseCatalogColor(hex))
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Color elegido",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
