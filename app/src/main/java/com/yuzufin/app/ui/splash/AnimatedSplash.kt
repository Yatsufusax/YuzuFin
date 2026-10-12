package com.yuzufin.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzufin.app.R
import com.yuzufin.app.ui.theme.BrandBright
import com.yuzufin.app.ui.theme.Forest
import com.yuzufin.app.ui.theme.MintTile
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Colores de la billetera (solo se usan aquí).
private val PocketColor = MintTile
private val FlapOuterColor = Color(0xFFDDF9E9)
private val LiningColor = Color(0xFF14634A)
private val FlapInnerColor = Color(0xFF1B7A5A)

// Curva con rebote suave al final (easeOutBack) para que el billete “salte” al salir.
private val OvershootEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

// Tiempos de la secuencia, en milisegundos.
private const val APPEAR_MS = 450
private const val LID_DELAY_MS = 450
private const val LID_MS = 650
private const val BILL_DELAY_MS = 900
private const val BILL_MS = 650
private const val TITLE_DELAY_MS = 1300
private const val TITLE_MS = 550
private const val TAGLINE_DELAY_MS = 1650
private const val TAGLINE_MS = 450
private const val HOLD_MS = 600L

/**
 * Splash animada: aparece la billetera, se abre la solapa, sale un billete y debajo
 * se revela el nombre “YuzuFin” con el lema. Dura unos 2,6 s y se puede saltar tocando.
 *
 * [onFinished] puede llamarse más de una vez (animación terminada y toque), así que
 * quien lo reciba debe ser idempotente.
 */
@Composable
fun AnimatedSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appear = remember { Animatable(0f) }
    val lid = remember { Animatable(0f) }
    val bill = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { appear.animateTo(1f, tween(APPEAR_MS, easing = FastOutSlowInEasing)) }
            launch {
                delay(LID_DELAY_MS.toLong())
                lid.animateTo(1f, tween(LID_MS, easing = FastOutSlowInEasing))
            }
            launch {
                delay(BILL_DELAY_MS.toLong())
                bill.animateTo(1f, tween(BILL_MS, easing = OvershootEasing))
            }
            launch {
                delay(TITLE_DELAY_MS.toLong())
                title.animateTo(1f, tween(TITLE_MS, easing = FastOutSlowInEasing))
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
        Canvas(
            modifier = Modifier
                .size(width = WALLET_CANVAS_WIDTH.dp, height = WALLET_CANVAS_HEIGHT.dp)
                .graphicsLayer {
                    val scale = 0.82f + 0.18f * appear.value
                    alpha = appear.value
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            drawWallet(lid = lid.value, bill = bill.value)
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.app_name),
            color = Color(0xFFF3FFF8),
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.graphicsLayer {
                alpha = title.value
                translationY = (1f - title.value) * 24.dp.toPx()
            },
        )

        Spacer(Modifier.height(8.dp))

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

// Lienzo de la billetera, en dp. Todas las medidas de abajo usan este mismo sistema.
private const val WALLET_CANVAS_WIDTH = 220f
private const val WALLET_CANVAS_HEIGHT = 210f

/**
 * Dibuja la billetera. [lid] va de 0 (cerrada) a 1 (abierta); [bill] de 0 (escondido)
 * a 1 (fuera), con un pequeño rebote por encima de 1.
 *
 * Orden de dibujo (de atrás hacia adelante):
 *  1. brillo, 2. interior, 3. solapa si ya está abierta (queda detrás), 4. billete,
 *  5. bolsillo frontal, 6. broche, 7. solapa si todavía está cerrada (queda delante).
 */
private fun DrawScope.drawWallet(lid: Float, bill: Float) {
    val u = density

    val left = 44f * u
    val right = 176f * u
    val top = 84f * u
    val bottom = 180f * u
    val width = right - left
    val centerX = (left + right) / 2f
    val corner = CornerRadius(20f * u)
    val flapHeight = 44f * u
    val pocketTop = 100f * u

    // 1. Brillo verde que crece al abrirse.
    val glowRadius = 150f * u
    val glowCenter = Offset(centerX, 120f * u)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(BrandBright.copy(alpha = 0.26f * lid), Color.Transparent),
            center = glowCenter,
            radius = glowRadius,
        ),
        radius = glowRadius,
        center = glowCenter,
    )

    // 2. Interior (visible cuando la solapa se abre).
    drawRoundRect(
        color = LiningColor,
        topLeft = Offset(left, top),
        size = Size(width, bottom - top),
        cornerRadius = corner,
    )

    // La solapa gira sobre su borde superior: el alto aparente es cos(ángulo).
    // Pasados 90° se ve su cara interior y queda detrás del billete.
    val angle = lid * 172f * (PI.toFloat() / 180f)
    val flapIsOpen = angle > PI.toFloat() / 2f

    fun drawFlap() {
        withTransform({
            scale(
                scaleX = 1f - 0.08f * sin(angle),
                scaleY = cos(angle),
                pivot = Offset(centerX, top),
            )
        }) {
            drawRoundRect(
                color = if (flapIsOpen) FlapInnerColor else FlapOuterColor,
                topLeft = Offset(left, top),
                size = Size(width, flapHeight),
                cornerRadius = corner,
            )
        }
    }

    // 3. Solapa abierta (detrás).
    if (flapIsOpen) drawFlap()

    // 4. Billete: sube 72 dp desde el interior.
    val billWidth = 100f * u
    val billHeight = 72f * u
    val billLeft = centerX - billWidth / 2f
    val billTop = (108f - 72f * bill) * u
    drawRoundRect(
        color = BrandBright,
        topLeft = Offset(billLeft, billTop),
        size = Size(billWidth, billHeight),
        cornerRadius = CornerRadius(12f * u),
    )
    drawCircle(
        color = Forest.copy(alpha = 0.85f),
        radius = 11f * u,
        center = Offset(billLeft + 26f * u, billTop + 24f * u),
    )
    drawRoundRect(
        color = Forest.copy(alpha = 0.55f),
        topLeft = Offset(billLeft + 46f * u, billTop + 16f * u),
        size = Size(38f * u, 6f * u),
        cornerRadius = CornerRadius(3f * u),
    )
    drawRoundRect(
        color = Forest.copy(alpha = 0.55f),
        topLeft = Offset(billLeft + 46f * u, billTop + 30f * u),
        size = Size(26f * u, 6f * u),
        cornerRadius = CornerRadius(3f * u),
    )

    // 5. Bolsillo frontal: borde superior recto (queda bajo la solapa), inferior redondeado.
    val pocket = Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = pocketTop,
                right = right,
                bottom = bottom,
                topLeftCornerRadius = CornerRadius.Zero,
                topRightCornerRadius = CornerRadius.Zero,
                bottomRightCornerRadius = corner,
                bottomLeftCornerRadius = corner,
            ),
        )
    }
    drawPath(path = pocket, color = PocketColor)

    // 6. Broche.
    drawRoundRect(
        color = BrandBright,
        topLeft = Offset(152f * u, 134f * u),
        size = Size(34f * u, 24f * u),
        cornerRadius = CornerRadius(12f * u),
    )
    drawCircle(
        color = Forest,
        radius = 4f * u,
        center = Offset(168f * u, 146f * u),
    )

    // 7. Solapa cerrada (delante).
    if (!flapIsOpen) drawFlap()
}
