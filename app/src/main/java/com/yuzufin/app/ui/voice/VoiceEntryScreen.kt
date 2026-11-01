package com.yuzufin.app.ui.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yuzufin.app.R
import com.yuzufin.app.domain.VoiceParser

object VoiceEntryDefaults {
    /** Idioma del reconocimiento según la moneda elegida (el acento ayuda a entender los números). */
    fun languageTag(currencyCode: String): String = when (currencyCode) {
        "PYG" -> "es-PY"
        "ARS" -> "es-AR"
        "EUR" -> "es-ES"
        else -> "es-419"
    }
}

private enum class VoiceStatus { LISTENING, NO_AMOUNT, NO_SPEECH, ERROR, UNAVAILABLE, DENIED }

/**
 * Pantalla completa (no es un Dialog) para anotar con la voz. Escucha apenas se abre; al terminar
 * pasa lo dicho al formulario, que lo muestra prellenado para que la persona lo revise. Siempre hay
 * una salida a mano. El micrófono se usa solo mientras esta pantalla está abierta.
 */
@Composable
fun VoiceEntryScreen(
    languageTag: String,
    onResult: (String) -> Unit,
    onManual: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val result by rememberUpdatedState(onResult)
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf(VoiceStatus.LISTENING) }
    var partial by remember { mutableStateOf("") }
    var heard by remember { mutableStateOf("") }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) {
        if (!granted) askPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    DisposableEffect(granted, attempt, languageTag) {
        if (!granted) return@DisposableEffect onDispose { }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status = VoiceStatus.UNAVAILABLE
            return@DisposableEffect onDispose { }
        }
        status = VoiceStatus.LISTENING
        partial = ""
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }

            override fun onError(error: Int) {
                status = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceStatus.NO_SPEECH
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceStatus.DENIED
                    else -> VoiceStatus.ERROR
                }
            }

            override fun onResults(results: Bundle?) {
                val options = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                // De las alternativas, la primera en la que se entiende un importe.
                val pick = options.firstOrNull { VoiceParser.extractAmount(it) != null }
                if (pick != null) {
                    result(pick)
                } else {
                    heard = options.firstOrNull().orEmpty()
                    status = if (heard.isBlank()) VoiceStatus.NO_SPEECH else VoiceStatus.NO_AMOUNT
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Prefiere reconocer en el teléfono (sin enviar el audio) si el idioma está descargado.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        recognizer.startListening(intent)
        onDispose {
            recognizer.cancel()
            recognizer.destroy()
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth()) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.voice_close))
                }
                Text(
                    stringResource(R.string.voice_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Spacer(Modifier.weight(1f))

            val listening = granted && status == VoiceStatus.LISTENING
            val micDescription = stringResource(R.string.voice_mic_cd)
            Mic(
                active = listening,
                onClick = {
                    if (!granted) askPermission.launch(Manifest.permission.RECORD_AUDIO) else attempt++
                },
                description = micDescription,
            )
            Spacer(Modifier.height(24.dp))

            val message = when {
                !granted -> stringResource(R.string.voice_permission_needed)
                else -> when (status) {
                    VoiceStatus.LISTENING -> if (partial.isBlank()) stringResource(R.string.voice_listening) else "“$partial”"
                    VoiceStatus.NO_AMOUNT -> stringResource(R.string.voice_no_amount)
                    VoiceStatus.NO_SPEECH -> stringResource(R.string.voice_no_speech)
                    VoiceStatus.ERROR -> stringResource(R.string.voice_error)
                    VoiceStatus.UNAVAILABLE -> stringResource(R.string.voice_unavailable)
                    VoiceStatus.DENIED -> stringResource(R.string.voice_permission_needed)
                }
            }
            Text(
                message,
                style = if (listening && partial.isNotBlank()) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (granted && status == VoiceStatus.NO_AMOUNT && heard.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.voice_heard, heard),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (listening && partial.isBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.voice_example),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))

            if (!granted && denied) {
                Button(
                    onClick = { askPermission.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.voice_allow)) }
                Spacer(Modifier.height(8.dp))
            } else if (granted && status != VoiceStatus.LISTENING && status != VoiceStatus.UNAVAILABLE) {
                Button(
                    onClick = { attempt++ },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.voice_retry)) }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = onManual,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.voice_manual)) }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.voice_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Botón grande del micrófono; late mientras se escucha. */
@Composable
private fun Mic(active: Boolean, onClick: () -> Unit, description: String) {
    val transition = rememberInfiniteTransition(label = "mic")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "micPulse",
    )
    Box(contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                Modifier
                    .size(132.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            )
        }
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(52.dp),
            )
        }
    }
}
