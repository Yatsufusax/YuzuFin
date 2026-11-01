package com.yuzufin.app.ui.components

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Cómo viene el mes, según el monito. Cada ánimo tiene su pose y varios mensajes. */
enum class MascotMood(val pose: MascotPose) {
    AWAY(MascotPose.MISSED_YOU),
    NEGATIVE(MascotPose.BROKE),
    PENDING(MascotPose.NOTIFICATION),
    EMPTY(MascotPose.WELCOME),
    CAREFUL(MascotPose.OVERSPENT),
    RICH(MascotPose.RICH),
    GOOD(MascotPose.PROGRESS),
}

/**
 * Un mensaje del monito. El texto recibe un número como `%1$d`: los días sin entrar en AWAY y el
 * porcentaje gastado del ingreso en los demás ánimos (los textos que no lo usan lo ignoran).
 */
data class MascotLine(@StringRes val textRes: Int)

object MascotTalk {
    /** Milisegundos entre letra y letra cuando el monito "tipea" el mensaje. */
    const val TYPE_MS = 32L

    /** Cuánto queda el mensaje completo en pantalla antes de pasar al siguiente. */
    const val HOLD_MS = 5_000L

    /** Siguiente mensaje: siempre distinto del actual. [random] puede ser cualquier entero. */
    fun nextIndex(current: Int, size: Int, random: Int): Int {
        if (size <= 1) return 0
        val step = 1 + random.mod(size - 1)
        return (current.mod(size) + step) % size
    }

    /** Porcentaje del ingreso que ya se gastó, entre 0 y 999. Sin ingreso da 0. */
    fun spentPercent(expense: Long, income: Long): Int =
        if (income <= 0L) 0 else (expense * 100L / income).coerceIn(0L, 999L).toInt()

    fun lines(mood: MascotMood): List<MascotLine> = when (mood) {
        MascotMood.AWAY -> listOf(
            MascotLine(R.string.mascot_msg_away),
            MascotLine(R.string.mascot_away_2),
            MascotLine(R.string.mascot_away_3),
            MascotLine(R.string.mascot_away_4),
        )
        MascotMood.NEGATIVE -> listOf(
            MascotLine(R.string.mascot_msg_negative),
            MascotLine(R.string.mascot_negative_2),
            MascotLine(R.string.mascot_negative_3),
            MascotLine(R.string.mascot_negative_4),
        )
        MascotMood.PENDING -> listOf(
            MascotLine(R.string.mascot_msg_pending),
            MascotLine(R.string.mascot_pending_2),
            MascotLine(R.string.mascot_pending_3),
        )
        MascotMood.EMPTY -> listOf(
            MascotLine(R.string.mascot_msg_empty_month),
            MascotLine(R.string.mascot_empty_2),
            MascotLine(R.string.mascot_empty_3),
        )
        MascotMood.CAREFUL -> listOf(
            MascotLine(R.string.mascot_msg_careful),
            MascotLine(R.string.mascot_careful_2),
            MascotLine(R.string.mascot_careful_3),
            MascotLine(R.string.mascot_careful_4),
        )
        MascotMood.RICH -> listOf(
            MascotLine(R.string.mascot_msg_rich),
            MascotLine(R.string.mascot_rich_2),
            MascotLine(R.string.mascot_rich_3),
            MascotLine(R.string.mascot_rich_4),
        )
        MascotMood.GOOD -> listOf(
            MascotLine(R.string.mascot_msg_good),
            MascotLine(R.string.mascot_good_2),
            MascotLine(R.string.mascot_good_3),
            MascotLine(R.string.mascot_good_4),
        )
    }
}

/** Los mensajes cambian solos salvo con las animaciones apagadas o con TalkBack (leería todo cada vez). */
@Composable
private fun rememberAutoRotate(): Boolean {
    val context = LocalContext.current
    val animations = animationsEnabled()
    return remember(context, animations) {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        animations && manager?.isTouchExplorationEnabled != true
    }
}

/**
 * El monito con su globo de texto ([pose] reemplaza a la del ánimo), como si te estuviera hablando: cada mensaje se va "tipeando"
 * letra por letra, queda un rato completo y pasa solo al siguiente (siempre distinto). Tocar el
 * globo mientras tipea lo completa; tocarlo completo, o tocar al monito, pasa al próximo mensaje.
 * Con las animaciones del teléfono apagadas el texto aparece entero, y con TalkBack no cambia solo
 * (leería todo cada vez). Los puntitos de abajo muestran cuántos mensajes hay.
 */
@Composable
fun TalkingMascot(
    mood: MascotMood,
    greeting: String,
    number: Int,
    modifier: Modifier = Modifier,
    pose: MascotPose = mood.pose,
) {
    val lines = remember(mood) { MascotTalk.lines(mood) }
    var index by rememberSaveable(mood) { mutableStateOf(Random.nextInt(lines.size)) }
    val autoRotate = rememberAutoRotate()
    val animations = animationsEnabled()
    val full = stringResource(lines[index.coerceIn(0, lines.lastIndex)].textRes, number)
    var shown by remember(full) { mutableIntStateOf(0) }
    val typing = shown < full.length

    fun advance() {
        index = MascotTalk.nextIndex(index, lines.size, Random.nextInt(1000))
    }

    // Tipea el mensaje, lo deja un rato y pasa al siguiente. Cambiar de mensaje reinicia todo.
    LaunchedEffect(mood, index, full, autoRotate, animations) {
        shown = 0
        if (animations) {
            while (shown < full.length) {
                delay(MascotTalk.TYPE_MS)
                shown++
            }
        } else {
            shown = full.length
        }
        if (!autoRotate || lines.size < 2) return@LaunchedEffect
        delay(MascotTalk.HOLD_MS)
        advance()
    }

    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Mascot(
            pose = pose,
            size = 96.dp,
            autoClip = true,
            onClick = { advance() },
        )
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .clip(shape)
                    .clickable(onClickLabel = stringResource(R.string.mascot_next_hint)) {
                        if (typing) shown = full.length else advance()
                    }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(greeting, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                // El texto entero, invisible, reserva el alto para que la tarjeta no salte mientras se tipea.
                Box {
                    Text(
                        full,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Transparent,
                    )
                    Text(
                        text = full.take(shown) + if (typing) "|" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
                if (lines.size > 1) {
                    Row(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clearAndSetSemantics { },
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(lines.size) { i ->
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == index) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
