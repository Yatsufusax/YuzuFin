package com.yuzufin.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzufin.app.R
import com.yuzufin.app.ui.theme.BrandBright
import com.yuzufin.app.ui.theme.Forest
import com.yuzufin.app.ui.theme.MintTile
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Proporción del logo (monea_logo.webp: 1100 x 776).
private const val LOGO_ASPECT = 1100f / 776f

// Tiempos de la secuencia, en milisegundos.
private const val GLOW_MS = 900
private const val LOGO_FADE_MS = 380
private const val TAGLINE_DELAY_MS = 1250
private const val TAGLINE_MS = 500
private const val HOLD_MS = 1000L

/**
 * Pantalla de inicio animada: el logo de Monea aparece con un rebote suave sobre un brillo verde
 * que crece detrás, después flota despacio y, debajo, se revela el lema. Dura unos 2,8 s y se
 * puede saltar tocando.
 *
 * [onFinished] puede llamarse más de una vez (animación terminada y toque), así que
 * quien lo reciba debe ser idempotente.
 */
@Composable
fun AnimatedSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glow = remember { Animatable(0f) }
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.55f) }
    val tagline = remember { Animatable(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)

    // Flotación continua y muy leve, para que el logo se sienta vivo mientras se lee.
    val float = rememberInfiniteTransition(label = "logoFloat")
    val bob by float.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val tilt by float.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2300, easing = LinearEasing), RepeatMode.Reverse),
        label = "tilt",
    )

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { glow.animateTo(1f, tween(GLOW_MS, easing = FastOutSlowInEasing)) }
            launch { logoAlpha.animateTo(1f, tween(LOGO_FADE_MS, easing = LinearOutSlowInEasing)) }
            launch {
                logoScale.animateTo(
                    1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                )
            }
            launch {
                delay(TAGLINE_DELAY_MS.toLong())
                tagline.animateTo(1f, tween(TAGLINE_MS, easing = LinearOutSlowInEasing))
            }
        }
        delay(HOLD_MS)
        currentOnFinished()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Forest)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFinished,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .drawBehind {
                    // Brillo verde que crece detrás del logo.
                    val radius = size.width * 0.62f * glow.value
                    if (radius > 0f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(BrandBright.copy(alpha = 0.30f * glow.value), Color.Transparent),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = radius,
                            ),
                            radius = radius,
                            center = Offset(size.width / 2f, size.height / 2f),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.monea_logo),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .aspectRatio(LOGO_ASPECT)
                    .graphicsLayer {
                        alpha = logoAlpha.value
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        translationY = bob * 5.dp.toPx() * logoAlpha.value
                        rotationZ = tilt * 1.2f * logoAlpha.value
                    },
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.tagline),
            color = MintTile,
            fontSize = 16.sp,
            modifier = Modifier.graphicsLayer {
                alpha = tagline.value
                translationY = (1f - tagline.value) * 12.dp.toPx()
            },
        )
    }
}
