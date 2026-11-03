package com.yuzufin.app.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuzufin.app.R

/**
 * Chat con Monki: respuestas a la izquierda con su carita, tus mensajes a la derecha, atajos con
 * preguntas frecuentes y el campo para escribir abajo. Pantalla completa (sin barra inferior).
 */
@Composable
fun MonkiChatScreen(
    viewModel: MonkiViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Siempre se ve lo último: un mensaje nuevo o el aviso "revisando…" bajan la lista.
    val lastIndex = state.messages.size + (if (state.waiting) 1 else 0) - 1
    LaunchedEffect(lastIndex) {
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    val submit = {
        val text = draft.trim()
        if (text.isNotEmpty() && !state.waiting) {
            draft = ""
            viewModel.send(text)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ChatHeader(currencyCode = state.currencyCode, onBack = onBack)

        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                text = stringResource(R.string.monki_privacy),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.messages, key = { it.id }) { message ->
                if (message.fromUser) {
                    UserBubble(message.text)
                } else {
                    MonkiBubble(message.text, state.currencyCode)
                }
            }
            if (state.waiting) {
                item(key = "waiting") {
                    MonkiBubble(stringResource(R.string.monki_thinking), state.currencyCode, subdued = true)
                }
            }
        }

        Suggestions(enabled = !state.waiting, onPick = viewModel::send)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(300) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.monki_placeholder)) },
                maxLines = 3,
                shape = RoundedCornerShape(26.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
            FilledIconButton(
                onClick = { submit() },
                enabled = draft.isNotBlank() && !state.waiting,
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.monki_send))
            }
        }
    }
}

@Composable
private fun ChatHeader(currencyCode: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        MonkiAvatar(currencyCode = currencyCode, size = 48.dp)
        Column {
            Text(
                stringResource(R.string.monki_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.monki_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Respuesta de Monki: su carita a la izquierda y el globo con la esquina de abajo a la izquierda recta. */
@Composable
private fun MonkiBubble(text: String, currencyCode: String, subdued: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MonkiAvatar(currencyCode = currencyCode, size = 36.dp)
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (subdued) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Mensaje de la persona: a la derecha, con el color principal del tema. */
@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Preguntas frecuentes a un toque: la conversación no tiene que empezar con una hoja en blanco. */
@Composable
private fun Suggestions(enabled: Boolean, onPick: (String) -> Unit) {
    val month = stringResource(R.string.monki_suggest_month)
    val week = stringResource(R.string.monki_suggest_week)
    val categories = stringResource(R.string.monki_suggest_categories)
    val balance = stringResource(R.string.monki_suggest_balance)
    val compare = stringResource(R.string.monki_suggest_compare)
    val check = stringResource(R.string.monki_suggest_check)
    val income = stringResource(R.string.monki_suggest_income)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(listOf(month, week, categories, balance, compare, check, income)) { suggestion ->
            AssistChip(
                onClick = { onPick(suggestion) },
                enabled = enabled,
                label = { Text(suggestion) },
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}
