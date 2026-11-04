package com.yuzufin.app.ui.assistant

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R
import com.yuzufin.app.ui.components.Mascot
import com.yuzufin.app.ui.components.MascotPose
import com.yuzufin.app.ui.components.animationsEnabled

private const val SLIDE_MS = 5_500

private class Slide(
    @StringRes val tag: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    val pose: MascotPose,
    val accent: Int,
)

private val slides = listOf(
    Slide(R.string.monki_story_1_tag, R.string.monki_story_1_title, R.string.monki_story_1_body, MascotPose.WELCOME, 0),
    Slide(R.string.monki_story_2_tag, R.string.monki_story_2_title, R.string.monki_story_2_body, MascotPose.QUESTION, 1),
    Slide(R.string.monki_story_3_tag, R.string.monki_story_3_title, R.string.monki_story_3_body, MascotPose.PLANNING, 2),
    Slide(R.string.monki_story_4_tag, R.string.monki_story_4_title, R.string.monki_story_4_body, MascotPose.CONTROL, 0),
    Slide(R.string.monki_story_5_tag, R.string.monki_story_5_title, R.string.monki_story_5_body, MascotPose.TIP, 1),
)

/**
 * Presentación de Monki en formato "historias": barritas de progreso arriba, fondo de color con
 * el monito y una tarjeta blanca abajo. Avanza sola; un toque a la derecha sigue, a la izquierda
 * vuelve y mantener apretado la pausa. Con las animaciones del teléfono apagadas no avanza sola.
 */
@Composable
fun MonkiStoryScreen(onFinish: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val auto = animationsEnabled()
    val last = slides.lastIndex
    val progress = remember(index) { Animatable(0f) }

    val next: () -> Unit = {
        if (index < last) index += 1 else onFinish()
    }
    val previous: () -> Unit = {
        if (index > 0) index -= 1
    }

    BackHandler(onBack = onFinish)

    LaunchedEffect(index, paused, auto) {
        if (!auto || paused) return@LaunchedEffect
        progress.animateTo(1f, tween(((1f - progress.value) * SLIDE_MS).toInt(), easing = LinearEasing))
        next()
    }

    val slide = slides[index]
    val scheme = MaterialTheme.colorScheme
    // Los colores salen del tema: cambian con el modo claro/oscuro y con el color elegido.
    val top = when (slide.accent) { 0 -> scheme.primary; 1 -> scheme.secondary; else -> scheme.tertiary }
    val bottom = when (slide.accent) { 0 -> scheme.primaryContainer; 1 -> scheme.secondaryContainer; else -> scheme.tertiaryContainer }
    val onTop = when (slide.accent) { 0 -> scheme.onPrimary; 1 -> scheme.onSecondary; else -> scheme.onTertiary }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom))),
    ) {
        // Capa de gestos: debajo de los botones, para que el toque en la tarjeta no cambie de historia.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(index) {
                    detectTapGestures(
                        onPress = {
                            paused = true
                            tryAwaitRelease()
                            paused = false
                        },
                        onTap = { offset -> if (offset.x < size.width / 3f) previous() else next() },
                    )
                },
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    slides.indices.forEach { i ->
                        val fill = when {
                            i < index -> 1f
                            i == index -> if (auto) progress.value else 1f
                            else -> 0f
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(onTop.copy(alpha = 0.35f)),
                        ) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(onTop))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onFinish) {
                        Text(stringResource(R.string.monki_story_skip), color = onTop, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val size = minOf(300.dp, maxHeight)
                Mascot(pose = slide.pose, size = size, autoClip = true)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = scheme.surface,
                contentColor = scheme.onSurface,
            ) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 20.dp, top = 22.dp, bottom = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = CircleShape, color = scheme.primaryContainer) {
                            Text(
                                stringResource(slide.tag),
                                color = scheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                        Text(
                            stringResource(slide.title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(slide.body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    FilledIconButton(onClick = next, modifier = Modifier.size(52.dp)) {
                        Icon(
                            if (index == last) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = stringResource(
                                if (index == last) R.string.monki_story_start else R.string.monki_story_next,
                            ),
                        )
                    }
                }
            }
        }
    }
}
