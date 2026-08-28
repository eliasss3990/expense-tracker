package com.eliasgonzalez.expensetracker.ui.activitylog

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.model.ActivityEntry
import com.eliasgonzalez.expensetracker.domain.model.ActivityType
import com.eliasgonzalez.expensetracker.ui.common.EXPENSES_PAGE_SIZE
import com.eliasgonzalez.expensetracker.ui.common.EmptyState
import com.eliasgonzalez.expensetracker.ui.common.dayTimeFormatter
import com.eliasgonzalez.expensetracker.ui.common.fullDateFormatter
import com.eliasgonzalez.expensetracker.ui.theme.BrandPrimary
import com.eliasgonzalez.expensetracker.ui.theme.ExpenseNegative
import com.eliasgonzalez.expensetracker.ui.theme.IncomePositive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun activityIcon(type: ActivityType) = when (type) {
    ActivityType.EXPENSE_CREATED, ActivityType.CANDIDATE_ACCEPTED -> Icons.Filled.CheckCircle
    ActivityType.EXPENSE_EDITED, ActivityType.CANDIDATE_EDITED -> Icons.Filled.Create
    ActivityType.EXPENSE_DELETED -> Icons.Filled.Delete
    ActivityType.CANDIDATE_CREATED -> Icons.Filled.CreditCard
    ActivityType.CANDIDATE_REJECTED -> Icons.Filled.Close
}

internal fun activityColor(type: ActivityType) = when (type) {
    ActivityType.EXPENSE_CREATED, ActivityType.CANDIDATE_ACCEPTED -> IncomePositive
    ActivityType.CANDIDATE_REJECTED, ActivityType.EXPENSE_DELETED -> ExpenseNegative
    else -> BrandPrimary
}

internal fun activityLabel(type: ActivityType): String = when (type) {
    ActivityType.EXPENSE_CREATED -> "Gasto registrado"
    ActivityType.EXPENSE_EDITED -> "Gasto editado"
    ActivityType.EXPENSE_DELETED -> "Gasto eliminado"
    ActivityType.CANDIDATE_CREATED -> "Gasto detectado"
    ActivityType.CANDIDATE_ACCEPTED -> "Gasto aceptado"
    ActivityType.CANDIDATE_EDITED -> "Gasto editado y aceptado"
    ActivityType.CANDIDATE_REJECTED -> "Candidato rechazado"
}

@Composable
internal fun ActivityScreen() {
    val entries by ServiceLocator.get().observeActivity().collectAsState()
    var visibleCount by remember { mutableStateOf(EXPENSES_PAGE_SIZE) }

    if (entries.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.History,
            title = "Sin actividad todavía",
            subtitle = "Acá vas a ver la traza de cada gasto: detectado, aceptado, editado o rechazado.",
        )
        return
    }

    val visibleEntries = remember(entries, visibleCount) { entries.take(visibleCount) }
    val grouped = remember(visibleEntries) {
        visibleEntries.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        grouped.forEach { (date, dayEntries) ->
            item {
                Text(
                    when (date) {
                        LocalDate.now() -> "HOY"
                        LocalDate.now().minusDays(1) -> "AYER"
                        else -> date.format(fullDateFormatter).uppercase()
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            items(dayEntries, key = { it.id }) { entry -> ActivityRow(entry) }
        }

        if (visibleCount < entries.size) {
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
private fun ActivityRow(entry: ActivityEntry) {
    val time = remember(entry.timestamp) {
        Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).format(dayTimeFormatter)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(activityColor(entry.type).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                activityIcon(entry.type),
                contentDescription = null,
                tint = activityColor(entry.type),
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(activityLabel(entry.type), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                entry.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
