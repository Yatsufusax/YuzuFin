package com.yuzufin.app.ui.components

import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Reproduce una sola vez un WebP animado del monito (res/drawable-nodpi/monito_clip_*.webp) y
 * avisa con [onEnd] cuando termina, o enseguida si no se pudo reproducir. Usa la API de Android
 * (ImageDecoder), así que no hace falta ninguna librería; exige Android 9 (API 28) o más.
 */
@RequiresApi(Build.VERSION_CODES.P)
@Composable
internal fun MascotClipPlayer(
    @DrawableRes res: Int,
    fit: ClipFit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val end by rememberUpdatedState(onEnd)
    AndroidView(
        modifier = modifier.graphicsLayer {
            // Iguala el tamaño y la posición del monito con los de la imagen fija.
            scaleX = fit.scale
            scaleY = fit.scale
            translationX = -fit.dx * size.height * fit.scale
            translationY = -fit.dy * size.height * fit.scale
        },
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                try {
                    val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ctx.resources, res))
                    setImageDrawable(drawable)
                    if (drawable is AnimatedImageDrawable) {
                        drawable.repeatCount = 0
                        drawable.registerAnimationCallback(
                            object : Animatable2.AnimationCallback() {
                                override fun onAnimationEnd(drawable: Drawable?) {
                                    end()
                                }
                            },
                        )
                        drawable.start()
                    } else {
                        post { end() }
                    }
                } catch (e: Exception) {
                    post { end() }
                }
            }
        },
        onRelease = { view -> (view.drawable as? AnimatedImageDrawable)?.stop() },
    )
}

/** Falso si la persona desactivó las animaciones del teléfono (Opciones de desarrollador o ahorro). */
@Composable
fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}
