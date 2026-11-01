package com.yuzufin.app.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuzufin.app.R
import com.yuzufin.app.rememberAppContainer
import kotlinx.coroutines.launch

private const val INTRO_ID = "monki_intro"

/**
 * Tarjeta de presentación de Monki: aparece una sola vez en Inicio (a quien empieza y a quien
 * se actualiza) para que sepan que está ahí. Se guarda como un consejo cerrado
 * (`dismissedHints`), así que "Volver a mostrar los consejos" en Ayuda también la vuelve a mostrar.
 */
@Composable
fun MonkiIntroCard(currencyCode: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    // Hasta leer las preferencias no se muestra nada, para que no parpadee.
    val loaded = settings ?: return
    if (INTRO_ID in loaded.dismissedHints) return

    val dismiss: () -> Unit = { scope.launch { container.settingsRepository.dismissHint(INTRO_ID) } }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 12.dp, bottom = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MonkiAvatar(currencyCode = currencyCode, size = 64.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.monki_intro_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.monki_intro_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.hint_dismiss))
                }
                Button(
                    onClick = {
                        dismiss()
                        onOpen()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.monki_intro_open)) }
            }
        }
    }
}
