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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.yuzufin.app.R
import com.yuzufin.app.ui.theme.Forest
import kotlinx.coroutines.delay

/** Proporción del video de la intro (1280 x 720). */
private const val VIDEO_ASPECT = 16f / 9f

/** Si por algún motivo el video no termina, la app sigue igual después de este tiempo. */
private const val SAFETY_MS = 7_000L

/** Volumen de la intro (el teléfono en silencio o vibración la deja muda). */
private const val INTRO_VOLUME = 0.8f

/**
 * Intro de la app: reproduce `res/raw/monea_intro.mp4` (4 s) sobre el mismo verde oscuro de la
 * pantalla de inicio del sistema. Se salta tocando. Si el video no se puede reproducir, se muestra
 * la splash animada de siempre.
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
    val player = remember { arrayOfNulls<VideoView>(1) }
    val interaction = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        delay(SAFETY_MS)
        finish()
    }
    DisposableEffect(Unit) {
        onDispose { player[0]?.stopPlayback() }
    }

    // Tapa del mismo color hasta que sale el primer cuadro: así no se ve un rectángulo negro.
    val cover by animateFloatAsState(if (rendering) 0f else 1f, tween(150), label = "cover")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Forest)
            .clickable(interactionSource = interaction, indication = null) { finish() },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(VIDEO_ASPECT),
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
        if (cover > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = cover }
                    .background(Forest),
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
