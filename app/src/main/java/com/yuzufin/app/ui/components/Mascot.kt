package com.yuzufin.app.ui.components

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Ajuste de un clip para que el monito mida y quede igual que en la imagen fija (si no, al empezar
 * o terminar el clip "salta" de tamaño). El clip se recorta con aire alrededor, y la imagen fija
 * justo al monito: [scale] = alto del lienzo del clip / alto del monito en su primer cuadro, y
 * [dx], [dy] = distancia del centro del monito (primer cuadro) al centro del lienzo, como fracción
 * del alto del lienzo. Ver MONITO_ANIMADO_README.md.
 */
data class ClipFit(val scale: Float = 1f, val dx: Float = 0f, val dy: Float = 0f) {
    companion object {
        val None = ClipFit()
    }
}

/**
 * Las poses del monito. Cada una es una imagen con fondo transparente en res/drawable-nodpi.
 * El comentario de cada una dice cuándo aparece. [clip] es, si la pose lo tiene, un WebP animado
 * (monito_clip_*.webp) que se reproduce una vez al tocar al monito (Android 9 o más).
 */
enum class MascotPose(
    @DrawableRes val res: Int,
    @DrawableRes val clip: Int? = null,
    val clipFit: ClipFit = ClipFit.None,
) {
    /** Al entrar a la app sin novedades: arranque del mes. */
    WELCOME(R.drawable.monito_bienvenida),
    /** Hace muchos días que no abrías la app. */
    MISSED_YOU(R.drawable.monito_te_extrana),
    /** Se te acabó la plata (saldo negativo). */
    BROKE(R.drawable.monito_sin_plata),
    /** Tenés plata de sobra este mes. */
    RICH(R.drawable.monito_con_plata, R.drawable.monito_clip_con_plata, ClipFit(scale = 440f / 411f, dx = 17.5f / 440f, dy = 12.5f / 440f)),
    /** Gastaste más de lo que ingresó. */
    OVERSPENT(R.drawable.monito_gasto_de_mas),
    /** Meta cumplida. */
    GOAL_DONE(R.drawable.monito_objetivo),
    /** Estás ahorrando. */
    SAVING(R.drawable.monito_ahorrando),
    /** Alcancía: metas de ahorro vacías. */
    PIGGY(R.drawable.monito_alcancia),
    /** Abrazando una moneda: primer ingreso o pantalla vacía. */
    COIN_HUG(R.drawable.monito_moneda),
    /** ¿En qué gasto? Movimientos sin resultados. */
    WONDER(R.drawable.monito_en_que_gasto),
    /** Controlando gastos. */
    CONTROL(R.drawable.monito_control),
    /** Billetera junto al presupuesto. */
    BUDGET(R.drawable.monito_presupuesto),
    /** Pagando cuentas. */
    PAYING(R.drawable.monito_pagando),
    /** Aviso de la campanita. */
    NOTIFICATION(R.drawable.monito_notificaciones),
    /** Planificando con la compu. */
    PLANNING(R.drawable.monito_planificando),
    /** Progreso del mes. */
    PROGRESS(R.drawable.monito_progreso),
    /** Consejo del día. */
    TIP(R.drawable.monito_sugerencias),
    /** Preguntas: centro de ayuda. */
    QUESTION(R.drawable.monito_preguntas),
    /** Configuración inicial según la moneda. */
    MATE(R.drawable.monito_mate, R.drawable.monito_clip_mate_hd, ClipFit(scale = 440f / 436f, dx = -16.5f / 440f, dy = 1f / 440f)),
    COFFEE(R.drawable.monito_cafe, R.drawable.monito_clip_cafe, ClipFit(scale = 440f / 388f, dx = -8f / 440f, dy = 25f / 440f)),
    TERERE(R.drawable.monito_terere, R.drawable.monito_clip_terere, ClipFit(scale = 440f / 433f, dx = -1.5f / 440f, dy = 2.5f / 440f)),
}

/** Guaraníes: tereré con la camiseta de Paraguay. Pesos: mate con la de Argentina. Otras monedas: café. */
fun mascotForCurrency(currencyCode: String): MascotPose = when (currencyCode) {
    "PYG" -> MascotPose.TERERE
    "ARS" -> MascotPose.MATE
    else -> MascotPose.COFFEE
}

private const val AUTO_CLIP_FIRST_MS = 900L
private const val AUTO_CLIP_PAUSE_MS = 4_000L

/**
 * El monito de Monea. Respira suave todo el tiempo, cambia de pose con un efecto y, si se lo toca,
 * pega un saltito con una vibración corta (y, si la pose tiene clip, lo reproduce). [onClick] se
 * llama en cada toque. Con [autoClip], el clip de la pose se reproduce solo y se repite cada pocos segundos.
 */
@Composable
fun Mascot(
    pose: MascotPose,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    lively: Boolean = true,
    autoClip: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val transition = rememberInfiniteTransition(label = "mascot")
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val jump = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var playing by remember { mutableStateOf(false) }
    val animationsOn = animationsEnabled()
    // Con autoClip, el clip se reproduce solo: al aparecer y después cada tanto, sin que lo toquen.
    LaunchedEffect(pose, autoClip, animationsOn) {
        playing = false
        if (!autoClip || pose.clip == null || !animationsOn || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@LaunchedEffect
        delay(AUTO_CLIP_FIRST_MS)
        while (true) {
            playing = true
            snapshotFlow { playing }.first { !it }
            delay(AUTO_CLIP_PAUSE_MS)
        }
    }

    AnimatedContent(
        targetState = pose,
        transitionSpec = {
            (fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.85f)) togetherWith fadeOut(tween(120))
        },
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val b = if (lively) breathe else 0f
                scaleX = 1f + 0.02f * b
                scaleY = 1f + 0.035f * b
                transformOrigin = TransformOrigin(0.5f, 1f)
                translationY = -jump.value * 18.dp.toPx()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (pose.clip != null && animationsOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) playing = true
                onClick?.invoke()
                scope.launch {
                    jump.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
                    jump.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
            },
        label = "mascotPose",
    ) { current ->
        val clip = current.clip
        if (playing && clip != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            MascotClipPlayer(clip, current.clipFit, onEnd = { playing = false }, modifier = Modifier.fillMaxSize())
        } else {
            Image(
                painter = painterResource(current.res),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
