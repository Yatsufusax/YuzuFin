package com.yuzufin.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Entrada escalonada: el elemento aparece subiendo suave, con un retraso según su [index]. */
@Composable
fun Modifier.entrance(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        progress.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

/** Número que "cuenta" hasta [target] (desde 0 al abrir, o desde el valor anterior al cambiar). */
@Composable
fun rememberCountUp(target: Long, durationMillis: Int = 900): Long {
    var shown by remember { mutableLongStateOf(0L) }
    LaunchedEffect(target) {
        val start = shown
        animate(0f, 1f, animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)) { value, _ ->
            shown = start + ((target - start).toDouble() * value).toLong()
        }
        shown = target
    }
    return shown
}

/** Vibración corta para confirmar una acción. */
@Composable
fun rememberHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }
}

private class ConfettiPiece(
    val angle: Float,
    val speed: Float,
    val color: Color,
    val size: Float,
    val spin: Float,
    val round: Boolean,
)

private val confettiColors = listOf(
    Color(0xFF1B8A4C), Color(0xFF5DB85C), Color(0xFFF4C430), Color(0xFFFF8FA3), Color(0xFF5B8DEF), Color(0xFFFF9F43),
)

/** Lluvia de confeti. Cada vez que [trigger] sube, dispara una nueva ráfaga. No intercepta toques. */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    val pieces = remember(trigger) {
        List(70) {
            ConfettiPiece(
                angle = -(PI.toFloat() * (0.1f + 0.8f * Random.nextFloat())),
                speed = 0.25f + 0.55f * Random.nextFloat(),
                color = confettiColors[Random.nextInt(confettiColors.size)],
                size = 5f + 6f * Random.nextFloat(),
                spin = Random.nextFloat() * 2f - 1f,
                round = Random.nextBoolean(),
            )
        }
    }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1900, easing = LinearEasing))
        }
    }
    if (progress.value < 1f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val t = progress.value
            val origin = Offset(size.width / 2f, size.height * 0.4f)
            val fade = if (t > 0.7f) (1f - t) / 0.3f else 1f
            pieces.forEach { p ->
                val distance = p.speed * size.minDimension * (1f - (1f - t) * (1f - t))
                val x = origin.x + cos(p.angle) * distance
                val y = origin.y + sin(p.angle) * distance + size.height * 0.8f * t * t
                val color = p.color.copy(alpha = fade.coerceIn(0f, 1f))
                if (p.round) {
                    drawCircle(color, radius = p.size, center = Offset(x, y))
                } else {
                    rotate(degrees = p.spin * t * 540f, pivot = Offset(x, y)) {
                        drawRect(color, topLeft = Offset(x - p.size, y - p.size / 2f), size = Size(p.size * 2f, p.size))
                    }
                }
            }
        }
    }
}
