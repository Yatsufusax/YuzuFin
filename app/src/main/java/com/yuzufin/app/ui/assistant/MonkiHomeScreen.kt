package com.yuzufin.app.ui.assistant

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuzufin.app.R
import com.yuzufin.app.ui.components.Mascot
import com.yuzufin.app.ui.components.mascotForCurrency

/**
 * Inicio de Monki AI: saludo, Monki en el centro, atajos con preguntas y el campo para empezar a
 * charlar. La charla en sí está en [MonkiChatScreen]; acá solo se elige cómo arrancarla.
 * Monki sigue siendo de solo lectura: ningún atajo escribe ni cambia datos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonkiHomeScreen(
    viewModel: MonkiViewModel,
    userName: String,
    currencyCode: String,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onShowStory: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val hasConversation = state.messages.any { it.fromUser }

    val ask: (String) -> Unit = { question ->
        val text = question.trim()
        if (text.isNotEmpty() && !state.waiting) {
            draft = ""
            viewModel.send(text)
            onOpenChat()
        }
    }

    val micPrompt = stringResource(R.string.monki_home_mic_prompt)
    val micMissing = stringResource(R.string.monki_home_mic_missing)
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!heard.isNullOrBlank()) ask(heard)
        }
    }
    val startMic = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, micPrompt)
        try {
            micLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, micMissing, Toast.LENGTH_LONG).show()
        }
    }

    val qMonth = stringResource(R.string.monki_suggest_month)
    val qProjection = stringResource(R.string.monki_q_projection)
    val qAnts = stringResource(R.string.monki_q_ants)
    val qCut = stringResource(R.string.monki_q_cut)
    val qWhatIf = stringResource(R.string.monki_q_whatif)

    val scheme = MaterialTheme.colorScheme
    // Fondo del color del tema (contenedor primario): cambia con la paleta y con el modo claro/oscuro.
    val ink = scheme.onPrimaryContainer
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.primaryContainer)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = ink)
            }
            Box(Modifier.weight(1f))
            IconButton(onClick = onOpenChat) {
                Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.monki_home_history), tint = ink)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (userName.isBlank()) {
                    stringResource(R.string.monki_home_greeting_plain)
                } else {
                    stringResource(R.string.monki_home_greeting, userName.trim())
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.monki_home_ask),
                style = MaterialTheme.typography.titleMedium,
                color = ink,
                textAlign = TextAlign.Center,
            )
        }

        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val size = minOf(260.dp, maxHeight)
            Mascot(pose = mascotForCurrency(currencyCode), size = size, autoClip = true)
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (hasConversation) Pill(stringResource(R.string.monki_pill_continue), onOpenChat)
            Pill(stringResource(R.string.monki_pill_month)) { ask(qMonth) }
            Pill(stringResource(R.string.monki_pill_projection)) { ask(qProjection) }
            Pill(stringResource(R.string.monki_pill_ants)) { ask(qAnts) }
            Pill(stringResource(R.string.monki_pill_cut)) { ask(qCut) }
            Pill(stringResource(R.string.monki_pill_whatif)) { ask(qWhatIf) }
            Pill(stringResource(R.string.monki_pill_how), onShowStory)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(300) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.monki_home_input)) },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = scheme.surface,
                    unfocusedContainerColor = scheme.surface,
                    unfocusedBorderColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { ask(draft) }),
            )
            if (draft.isBlank()) {
                FilledIconButton(onClick = startMic, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.Mic, contentDescription = stringResource(R.string.monki_home_mic))
                }
            } else {
                FilledIconButton(onClick = { ask(draft) }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.monki_send))
                }
            }
        }

        Text(
            text = stringResource(R.string.monki_home_footer),
            style = MaterialTheme.typography.bodySmall,
            color = ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f)),
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
