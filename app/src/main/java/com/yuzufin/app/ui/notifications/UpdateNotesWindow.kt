package com.yuzufin.app.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzufin.app.R
import com.yuzufin.app.ui.components.Mascot
import com.yuzufin.app.ui.components.MascotPose

/** Una sección del texto guardado en la campanilla: título (opcional) y sus puntos. */
private data class NotesBlock(val heading: String?, val bullets: List<String>)

/** El texto guardado separa secciones con una línea en blanco; los puntos empiezan con “• ”. */
private fun parseNotes(body: String): List<NotesBlock> =
    body.split("\n\n").map { chunk ->
        val lines = chunk.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val bullets = lines.filter { it.startsWith("•") }.map { it.removePrefix("•").trim() }
        val heading = lines.firstOrNull { !it.startsWith("•") }
        NotesBlock(heading, bullets)
    }.filter { it.heading != null || it.bullets.isNotEmpty() }

/** Ventana aparte, a pantalla completa, para leer con calma lo que trae una actualización. */
@Composable
fun UpdateNotesWindow(title: String, body: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.notes_close))
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Mascot(MascotPose.TIP, size = 84.dp)
                        Text(
                            text = stringResource(R.string.notes_window_heading),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    parseNotes(body).forEach { block ->
                        block.heading?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        block.bullets.forEach { bullet ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("•", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.notes_close))
                }
            }
        }
    }
}
