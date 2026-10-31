package com.yuzufin.app.ui.splash

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuzufin.app.R
import com.yuzufin.app.ui.theme.MintTile
import kotlinx.coroutines.delay

/** Proporción del video de la intro (720 x 1280, vertical). */
private const val VIDEO_ASPECT = 9f / 16f

/**
 * Color de fondo del video en sus bordes (arriba y abajo es parejo). Se usa de fondo para que, en
 * pantallas más altas que el video, no se note dónde termina.
 */
private val VideoBackground = Color(0xFF112825)

/** Si por algún motivo el video no termina, la app sigue igual después de este tiempo. */
private const val SAFETY_MS = 7_000L

/** Volumen de la intro (el teléfono en silencio o vibración la deja muda). */
private const val INTRO_VOLUME = 0.8f

/** La frase aparece cuando el título ya terminó de entrar en el video. */
private const val TAGLINE_DELAY_MS = 1_600L

/** Altura de la frase como fracción del alto del video: justo debajo de las banderas del logo. */
private const val TAGLINE_TOP_FRACTION = 0.78f

/**
 * Intro de la app: reproduce `res/raw/monea_intro.mp4` (4 s, vertical) completo, sin recortar, y
 * debajo del logo muestra la frase de siempre. Se salta tocando. Si el video no se puede
 * reproducir, se muestra la splash animada de siempre (que también lleva la frase).
 *
 * [onFinished] puede llamarse más de una vez (fin del video, toque, red de seguridad), así que
 * quien lo reciba debe ser idempotente.
 */
@Composable
fun VideoSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var failed by remember { mutableStateOf(false) }
    if (failed) {
        AnimatedSplash(onFinished = onFinished, modifier = modifier)
    } else {
        VideoSplashBody(onFinished = onFinished, onFailed = { failed = true }, modifier = modifier)
    }
}

@Composable
private fun VideoSplashBody(
    onFinished: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier,
) {
    val finish by rememberUpdatedState(onFinished)
    val fail by rememberUpdatedState(onFailed)
    var rendering by remember { mutableStateOf(false) }
    var showTagline by remember { mutableStateOf(false) }
    val player = remember { arrayOfNulls<VideoView>(1) }
    val interaction = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        delay(SAFETY_MS)
        finish()
    }
    // El reloj de la frase arranca recién cuando sale el primer cuadro del video.
    LaunchedEffect(rendering) {
        if (rendering) {
            delay(TAGLINE_DELAY_MS)
            showTagline = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { player[0]?.stopPlayback() }
    }

    // Tapa del mismo color hasta que sale el primer cuadro: así no se ve un rectángulo negro.
    val cover by animateFloatAsState(if (rendering) 0f else 1f, tween(150), label = "cover")
    val tagline by animateFloatAsState(if (showTagline) 1f else 0f, tween(500), label = "tagline")

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(VideoBackground)
            .clickable(interactionSource = interaction, indication = null) { finish() },
        contentAlignment = Alignment.Center,
    ) {
        // El video se muestra entero: al ancho de la pantalla, o al alto si la pantalla es más ancha.
        val videoWidth = if (maxWidth / maxHeight < VIDEO_ASPECT) maxWidth else maxHeight * VIDEO_ASPECT
        val videoHeight = videoWidth / VIDEO_ASPECT

        Box(modifier = Modifier.size(videoWidth, videoHeight)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        player[0] = this
                        // No interrumpe la música que la persona esté escuchando.
                        setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE)
                        setOnPreparedListener { mp -> applyVolume(ctx, mp) }
                        setOnInfoListener { _, what, _ ->
                            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) rendering = true
                            false
                        }
                        setOnCompletionListener { finish() }
                        setOnErrorListener { _, _, _ ->
                            fail()
                            true
                        }
                        setVideoURI(Uri.parse("android.resource://${ctx.packageName}/${R.raw.monea_intro}"))
                        start()
                    }
                },
            )
            Text(
                text = stringResource(R.string.tagline),
                color = MintTile,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = videoHeight * TAGLINE_TOP_FRACTION)
                    .graphicsLayer {
                        alpha = tagline
                        translationY = (1f - tagline) * 10.dp.toPx()
                    },
            )
        }
        if (cover > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = cover }
                    .background(VideoBackground),
            )
        }
    }
}

/** Con el teléfono en silencio o vibración la intro no suena. */
private fun applyVolume(context: Context, player: MediaPlayer) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val quiet = audio == null || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
    val volume = if (quiet) 0f else INTRO_VOLUME
    player.setVolume(volume, volume)
}
