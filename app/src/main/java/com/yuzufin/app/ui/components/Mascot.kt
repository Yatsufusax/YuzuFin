package com.yuzufin.app.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/** Las poses del monito. Cada una es una imagen con fondo transparente en res/drawable-nodpi. */
enum class MascotPose(@DrawableRes val res: Int) {
    SAVING(R.drawable.mascot_ahorrando),
    PIGGY(R.drawable.mascot_guarda_dinero),
    INCOME(R.drawable.mascot_registrando_ingreso),
    EXPENSES(R.drawable.mascot_sumando_gastos),
    PROGRESS(R.drawable.mascot_viendo_progreso),
    PLANNING(R.drawable.mascot_planificando),
    GOALS(R.drawable.mascot_organizando_metas),
    CELEBRATE(R.drawable.mascot_lo_lograste),
    WALLET(R.drawable.mascot_cargando_billetera),
    NOTIFICATION(R.drawable.mascot_notificaciones),
    WONDER(R.drawable.mascot_en_que_gasto),
    CALM(R.drawable.mascot_respiro),
    TARGET(R.drawable.mascot_cumpliendo_objetivos),
    COINS(R.drawable.mascot_sumando_ahorros),
    MONEY_RAIN(R.drawable.mascot_ingresos),
    SAVINGS_GOAL(R.drawable.mascot_metas_ahorro),
    PAYING(R.drawable.mascot_pagando_cuentas),
    CONTROL(R.drawable.mascot_controlando_gastos),
    MOTIVATION(R.drawable.mascot_motivacion),
    FUTURE(R.drawable.mascot_futuro),
    ADVANCING(R.drawable.mascot_avanzando),
    AUTO_SAVING(R.drawable.mascot_ahorro_automatico),
    GO(R.drawable.mascot_vamos),
    SMALL_STEPS(R.drawable.mascot_pequenos_esfuerzos),
    DREAMS(R.drawable.mascot_suenos),
    MATE(R.drawable.mascot_mate_argentina),
    COFFEE(R.drawable.mascot_cafe),
    TERERE(R.drawable.mascot_terere_paraguay),
}

/** Guaraníes: tereré con la camiseta de Paraguay. Pesos: mate con la de Argentina. Otras monedas: café. */
fun mascotForCurrency(currencyCode: String): MascotPose = when (currencyCode) {
    "PYG" -> MascotPose.TERERE
    "ARS" -> MascotPose.MATE
    else -> MascotPose.COFFEE
}

/**
 * El monito de Monea. Respira suave todo el tiempo, cambia de pose con un efecto y, si se lo toca,
 * pega un saltito con una vibración corta.
 */
@Composable
fun Mascot(
    pose: MascotPose,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    lively: Boolean = true,
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
                scope.launch {
                    jump.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
                    jump.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
            },
        label = "mascotPose",
    ) { current ->
        Image(
            painter = painterResource(current.res),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
