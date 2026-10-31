package com.yuzufin.app.ui.tour

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R
import com.yuzufin.app.ui.components.Mascot
import com.yuzufin.app.ui.components.MascotPose

private data class TourStep(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val pose: MascotPose,
)

/** Los 3 pasos: anotar un gasto, ver el resumen y saber dónde pedir ayuda. */
private val tourSteps = listOf(
    TourStep(R.string.tour_1_title, R.string.tour_1_body, MascotPose.COIN_HUG),
    TourStep(R.string.tour_2_title, R.string.tour_2_body, MascotPose.PROGRESS),
    TourStep(R.string.tour_3_title, R.string.tour_3_body, MascotPose.QUESTION),
)

/**
 * Recorrido corto del primer uso: 3 pantallas con el monito, a pantalla completa, que se pueden
 * saltear en cualquier momento. Los botones principales están abajo, al alcance del pulgar.
 * [onFinish] se llama al terminar, al saltear o al volver atrás con el gesto del teléfono.
 */
@Composable
fun FirstUseTour(onFinish: () -> Unit) {
    var index by rememberSaveable { mutableStateOf(0) }
    val last = index == tourSteps.lastIndex

    // Se dibuja dentro de la app (no en un diálogo): así respeta la barra de gestos del teléfono.
    BackHandler(onBack = onFinish)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } },
        color = MaterialTheme.colorScheme.background,
    ) {
        run {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onFinish) { Text(stringResource(R.string.tour_skip)) }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Crossfade(targetState = index, label = "tourStep") { current ->
                        StepContent(tourSteps[current])
                    }
                }

                StepDots(
                    index = index,
                    total = tourSteps.size,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 12.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (index > 0) {
                        OutlinedButton(
                            onClick = { index -= 1 },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                        ) { Text(stringResource(R.string.tour_back)) }
                    }
                    Button(
                        onClick = { if (last) onFinish() else index += 1 },
                        modifier = Modifier
                            .weight(if (index > 0) 1.4f else 1f)
                            .heightIn(min = 52.dp),
                    ) {
                        Text(stringResource(if (last) R.string.tour_done else R.string.tour_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepContent(step: TourStep) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Mascot(step.pose, size = 190.dp)
        Text(
            text = stringResource(step.title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(step.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Puntitos de avance. Para TalkBack se lee como «Paso 2 de 3». */
@Composable
private fun StepDots(index: Int, total: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.tour_step_of, index + 1, total)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            val selected = i == index
            Box(
                modifier = Modifier
                    .height(10.dp)
                    .width(if (selected) 28.dp else 10.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    ),
            )
        }
    }
}
