package com.yuzufin.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

/**
 * Consejo del monito para explicar una pantalla. Aparece hasta que la persona toca «Entendido»;
 * se puede volver a mostrar desde Ayuda. [hintId] debe ser único por consejo.
 */
@Composable
fun HintCard(
    hintId: String,
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    modifier: Modifier = Modifier,
    pose: MascotPose = MascotPose.TIP,
) {
    val container = rememberAppContainer()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    // Hasta leer las preferencias no se muestra nada, para que no parpadee.
    val loaded = settings ?: return
    if (hintId in loaded.dismissedHints) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 14.dp, end = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Mascot(pose, size = 84.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    onClick = { scope.launch { container.settingsRepository.dismissHint(hintId) } },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.hint_dismiss)) }
            }
        }
    }
}
