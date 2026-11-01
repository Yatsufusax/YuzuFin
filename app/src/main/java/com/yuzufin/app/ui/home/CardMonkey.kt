package com.yuzufin.app.ui.home

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R
import com.yuzufin.app.ui.components.ClipFit
import com.yuzufin.app.ui.components.MascotClipPlayer
import com.yuzufin.app.ui.components.animationsEnabled
import kotlinx.coroutines.launch

/** En qué momento del giro está el monito de la tarjeta. */
private enum class FlipPhase { Idle, ToClip, Playing, ToStill }

private const val FLIP_MS = 520

/**
 * Un clip que se ve al darle vuelta al monito: el WebP y el tamaño (en dp) con el que se dibuja
 * sobre el monito fijo, que mide 128 dp de alto. Es más grande que el monito fijo para que lo que
 * sale del recuadro (papelitos, la cola, los brazos) no se corte.
 */
private data class CardClip(
    @DrawableRes val res: Int,
    val widthDp: Float,
    val heightDp: Float,
    /** Subida del clip para que el monito quede donde estaba el fijo (que se dibuja 3 dp más arriba). */
    val liftDp: Float = 0f,
)

// Argentina: 462x260, el monito ocupa cerca del 88% del alto. Los papelitos llenan la tarjeta.
private val ARGENTINA_CLIP = CardClip(R.drawable.monito_clip_mundial, 146f * 462f / 260f, 146f)

// Paraguay: 495x402 centrado en el monito del primer cuadro (mide 634 de 670 px de alto de lienzo,
// es decir 128 dp de 135,3 dp) para que no cambie de tamaño al girar.
private val PARAGUAY_CLIP = CardClip(R.drawable.monito_clip_paraguay, 166.8f, 135.3f, liftDp = 3f)

/**
 * El monito de la tarjeta de Inicio, con la camiseta de la moneda: Paraguay (guaraníes), Argentina
 * (pesos) o sin camiseta (el resto). Respira suave y, si se lo toca, pega un saltito.
 *
 * Con pesos argentinos o guaraníes, al tocarlo **gira como una carta** y aparece su clip: festejando
 * con la copa y papelitos (Argentina, unos 10 segundos) o comiendo una empanada con el termo del
 * tereré (Paraguay, 6 segundos). Al terminar (o si se lo vuelve a tocar) gira de vuelta. Los clips
 * exigen Android 9 o más y que las animaciones del teléfono estén activas; si no, el toque solo
 * hace el saltito.
 */
@Composable
internal fun CardMonkey(
    currencyCode: String,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    val still = when (currencyCode) {
        "PYG" -> R.drawable.monito_tarjeta_pyg
        "ARS" -> R.drawable.monito_tarjeta_ars
        else -> R.drawable.monito_tarjeta_neutral
    }
    val clip = when (currencyCode) {
        "ARS" -> ARGENTINA_CLIP
        "PYG" -> PARAGUAY_CLIP
        else -> null
    }
    val canCelebrate = clip != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && animationsEnabled()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val transition = rememberInfiniteTransition(label = "cardMonkey")
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val jump = remember { Animatable(0f) }
    val angle = remember { Animatable(0f) }
    var phase by remember { mutableStateOf(FlipPhase.Idle) }
    var clipEnded by remember { mutableStateOf(false) }

    LaunchedEffect(phase) {
        when (phase) {
            FlipPhase.ToClip -> {
                clipEnded = false
                angle.animateTo(180f, tween(FLIP_MS, easing = FastOutSlowInEasing))
                phase = if (clipEnded) FlipPhase.ToStill else FlipPhase.Playing
            }
            FlipPhase.ToStill -> {
                angle.animateTo(360f, tween(FLIP_MS, easing = FastOutSlowInEasing))
                angle.snapTo(0f)
                phase = FlipPhase.Idle
            }
            else -> Unit
        }
    }

    // De 90° a 270° se ve el reverso (el clip); el resto, el frente.
    val backVisible = angle.value > 90f && angle.value < 270f
    val description = stringResource(
        when (currencyCode) {
            "ARS" -> R.string.summary_card_monkey_ars
            "PYG" -> R.string.summary_card_monkey_pyg
            else -> R.string.summary_card_monkey
        },
    )
    val actionLabel = if (canCelebrate) {
        stringResource(
            if (currencyCode == "PYG") R.string.summary_card_monkey_pyg_action else R.string.summary_card_monkey_ars_action,
        )
    } else {
        null
    }

    Box(
        modifier = modifier
            .size(width = 136.dp, height = 128.dp)
            .graphicsLayer {
                rotationY = angle.value
                cameraDistance = 14f * density.density
                val b = if (phase == FlipPhase.Idle) breathe else 0f
                scaleX = 1f + 0.02f * b
                scaleY = 1f + 0.035f * b
                transformOrigin = TransformOrigin(0.5f, 1f)
                translationY = -jump.value * 14.dp.toPx()
            }
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = actionLabel,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (canCelebrate) {
                    when (phase) {
                        FlipPhase.Idle -> phase = FlipPhase.ToClip
                        // Un toque durante el festejo lo corta y el monito vuelve a girar.
                        FlipPhase.Playing -> phase = FlipPhase.ToStill
                        else -> Unit
                    }
                } else {
                    scope.launch {
                        jump.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
                        jump.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (backVisible) {
            if (clip != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // El reverso gira 180° para que el clip no se vea espejado.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .requiredSize(width = clip.widthDp.dp, height = clip.heightDp.dp)
                        .graphicsLayer {
                            rotationY = 180f
                            translationY = -clip.liftDp.dp.toPx()
                        },
                ) {
                    MascotClipPlayer(
                        res = clip.res,
                        fit = ClipFit.None,
                        onEnd = {
                            if (phase == FlipPhase.Playing) phase = FlipPhase.ToStill else clipEnded = true
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            // Una sombrita suave en el piso, el monito y unas rayitas de alegría arriba a la izquierda.
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.86f)
                    .height(14.dp),
            ) {
                drawOval(color = ink.copy(alpha = 0.16f), topLeft = Offset.Zero, size = Size(size.width, size.height))
            }
            Image(
                painter = painterResource(still),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = -3.dp.toPx() },
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val stroke = 2.5.dp.toPx()
                val tint = ink.copy(alpha = 0.80f)
                drawLine(tint, Offset(w * 0.17f, h * 0.17f), Offset(w * 0.09f, h * 0.12f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.24f, h * 0.10f), Offset(w * 0.20f, h * 0.02f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.33f, h * 0.07f), Offset(w * 0.33f, h * -0.01f), stroke, StrokeCap.Round)
            }
        }
    }
}
