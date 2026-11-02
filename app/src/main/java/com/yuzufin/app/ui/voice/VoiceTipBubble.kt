package com.yuzufin.app.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R
import com.yuzufin.app.ui.components.animationsEnabled

/**
 * Globito flotante sobre el botón "+" que avisa que, manteniéndolo apretado, se anota hablando.
 * Se toca para cerrarlo. Va pegado al borde de abajo, justo encima de la barra flotante
 * (alto de la barra: 12 dp de margen + 74 dp), con una flechita que apunta al "+".
 */
@Composable
fun VoiceTipBubble(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bubble = MaterialTheme.colorScheme.inverseSurface
    val onBubble = MaterialTheme.colorScheme.inverseOnSurface
    val animations = animationsEnabled()
    val transition = rememberInfiniteTransition(label = "voiceTip")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "voiceTipBob",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(bottom = 92.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 3 },
            exit = fadeOut(tween(180)),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    // Sube y baja suave para llamar la atención hacia el botón.
                    translationY = if (animations) -4.dp.toPx() * bob else 0f
                },
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = bubble,
                    contentColor = onBubble,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.voice_tip_close), onClick = onDismiss),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(22.dp))
                        Column {
                            Text(
                                stringResource(R.string.voice_tip_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(R.string.voice_tip_body),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                // Flechita hacia abajo, apuntando al "+".
                Canvas(Modifier.size(width = 18.dp, height = 9.dp)) {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width / 2f, size.height)
                        close()
                    }
                    drawPath(path, color = bubble)
                }
            }
        }
    }
}
