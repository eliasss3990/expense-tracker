package com.eliasgonzalez.expensetracker.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eliasgonzalez.expensetracker.di.ServiceLocator
import com.eliasgonzalez.expensetracker.domain.chat.ChatProposal
import com.eliasgonzalez.expensetracker.domain.chat.ChatReply
import com.eliasgonzalez.expensetracker.domain.log.AppLog
import com.eliasgonzalez.expensetracker.ui.theme.rememberCategoryLookup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class ChatMessage(val fromUser: Boolean, val text: String, val proposal: ChatProposal? = null)

private const val MAX_INPUT_CHARS = 400

/**
 * Chat con la IA local, a pantalla completa. La sesion vive solo mientras esta pantalla esta abierta: al salir y volver
 * a entrar es un chat nuevo, sin nada del anterior (no hay historial entre chats). El modelo solo interpreta; todo lo que
 * escribe (cargar, editar, borrar) se muestra como propuesta y solo se ejecuta con el boton Confirmar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreen(onClose: () -> Unit) {
    val container = remember { ServiceLocator.get() }
    val session = remember { container.newChatSession() }
    val categoryLookup = rememberCategoryLookup()
    val presentation = remember(categoryLookup) { ChatPresentation(categoryLabel = { categoryLookup(it).label }) }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var pending by remember { mutableStateOf<ChatProposal?>(null) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val modelReady = remember { container.localModelManager.isReady() }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun add(text: String, proposal: ChatProposal? = null, fromUser: Boolean = false) {
        messages += ChatMessage(fromUser, text, proposal)
    }

    fun send() {
        val phrase = input.trim()
        if (phrase.isEmpty() || busy || !modelReady) return
        input = ""
        add(phrase, fromUser = true)
        busy = true
        scope.launch {
            try {
                val reply = session.send(phrase, LocalDate.now(), container.chatCategories())
                add(presentation.text(reply), (reply as? ChatReply.Confirm)?.proposal)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.w("ChatScreen", "fallo el chat: ${error.javaClass.simpleName}")
                add("No pude consultar al modelo ahora. Probá de nuevo en un momento.")
            } finally {
                pending = session.pending
                busy = false
            }
        }
    }

    fun confirm() {
        val proposal = pending ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                val result = session.confirm()
                if (result != null) add(presentation.confirmed(result, proposal))
            } finally {
                pending = session.pending
                busy = false
            }
        }
    }

    fun cancel() {
        if (pending == null || busy) return
        session.cancel()
        pending = session.pending
        add(presentation.text(ChatReply.Cancelled))
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text("Asistente") },
            navigationIcon = {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar el chat") }
            },
            windowInsets = WindowInsets(0.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        if (modelReady) {
                            "Preguntame por tus gastos o pedime cargar, editar o borrar uno. Por ejemplo:\n" +
                                "• cuánto gasté hoy\n• anotá 25000 de nafta\n• borrá el último gasto\n\n" +
                                "Todo lo que cambie tus datos te lo muestro antes y lo confirmás vos."
                        } else {
                            "Para usar el asistente instalá el modelo local en el menú ⋮ → IA local."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(messages) { message ->
                Bubble(message)
                if (message.proposal != null && message.proposal === pending) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ::confirm, enabled = !busy) { Text("Confirmar") }
                        OutlinedButton(onClick = ::cancel, enabled = !busy) { Text("Cancelar") }
                    }
                }
            }
            if (busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
                        Text("Pensando…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(MAX_INPUT_CHARS) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribí acá") },
                enabled = modelReady,
                maxLines = 4,
            )
            IconButton(onClick = ::send, enabled = modelReady && !busy && input.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (message.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(message.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
