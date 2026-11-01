package com.yuzufin.app.ui.components

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.TextButton
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

/** Botón que puede acompañar a un mensaje del monito: lleva directo a lo que comenta. */
enum class MascotAction(@StringRes val labelRes: Int) {
    ADD_EXPENSE(R.string.mascot_act_add_expense),
    SEE_BUDGETS(R.string.mascot_act_budgets),
    SEE_SAVINGS(R.string.mascot_act_savings),
    SEE_STATS(R.string.mascot_act_stats),
    SEE_INCOMES(R.string.mascot_act_incomes),
}

/**
 * Un mensaje del monito. El texto recibe un número como `%1$d`: los días sin entrar en AWAY y el
 * porcentaje gastado del ingreso en los demás ánimos (los textos que no lo usan lo ignoran).
 */
data class MascotLine(@StringRes val textRes: Int, val action: MascotAction? = null)

object MascotTalk {
    /** Cada cuánto cambia solo el mensaje si la persona no lo toca. */
    const val ROTATE_MS = 12_000L

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
            MascotLine(R.string.mascot_msg_away, MascotAction.ADD_EXPENSE),
            MascotLine(R.string.mascot_away_2, MascotAction.ADD_EXPENSE),
            MascotLine(R.string.mascot_away_3, MascotAction.ADD_EXPENSE),
            MascotLine(R.string.mascot_away_4),
        )
        MascotMood.NEGATIVE -> listOf(
            MascotLine(R.string.mascot_msg_negative, MascotAction.SEE_STATS),
            MascotLine(R.string.mascot_negative_2, MascotAction.SEE_STATS),
            MascotLine(R.string.mascot_negative_3, MascotAction.SEE_BUDGETS),
            MascotLine(R.string.mascot_negative_4, MascotAction.ADD_EXPENSE),
        )
        MascotMood.PENDING -> listOf(
            MascotLine(R.string.mascot_msg_pending, MascotAction.SEE_INCOMES),
            MascotLine(R.string.mascot_pending_2, MascotAction.SEE_INCOMES),
            MascotLine(R.string.mascot_pending_3, MascotAction.SEE_INCOMES),
        )
        MascotMood.EMPTY -> listOf(
            MascotLine(R.string.mascot_msg_empty_month, MascotAction.ADD_EXPENSE),
            MascotLine(R.string.mascot_empty_2, MascotAction.ADD_EXPENSE),
            MascotLine(R.string.mascot_empty_3, MascotAction.ADD_EXPENSE),
        )
        MascotMood.CAREFUL -> listOf(
            MascotLine(R.string.mascot_msg_careful, MascotAction.SEE_BUDGETS),
            MascotLine(R.string.mascot_careful_2, MascotAction.SEE_STATS),
            MascotLine(R.string.mascot_careful_3, MascotAction.SEE_BUDGETS),
            MascotLine(R.string.mascot_careful_4),
        )
        MascotMood.RICH -> listOf(
            MascotLine(R.string.mascot_msg_rich, MascotAction.SEE_SAVINGS),
            MascotLine(R.string.mascot_rich_2, MascotAction.SEE_STATS),
            MascotLine(R.string.mascot_rich_3, MascotAction.SEE_SAVINGS),
            MascotLine(R.string.mascot_rich_4),
        )
        MascotMood.GOOD -> listOf(
            MascotLine(R.string.mascot_msg_good, MascotAction.SEE_STATS),
            MascotLine(R.string.mascot_good_2, MascotAction.SEE_STATS),
            MascotLine(R.string.mascot_good_3, MascotAction.ADD_EXPENSE),
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
 * El monito con su globo de texto. El mensaje cambia solo cada [MascotTalk.ROTATE_MS], al tocar al
 * monito o al tocar el globo; algunos mensajes traen un botón que lleva a la pantalla de la que
 * hablan. Los puntitos de abajo muestran cuántos mensajes hay.
 */
@Composable
fun TalkingMascot(
    mood: MascotMood,
    greeting: String,
    number: Int,
    onAction: (MascotAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = remember(mood) { MascotTalk.lines(mood) }
    var index by rememberSaveable(mood) { mutableStateOf(Random.nextInt(lines.size)) }
    var touches by remember { mutableIntStateOf(0) }
    val autoRotate = rememberAutoRotate()

    fun advance() {
        index = MascotTalk.nextIndex(index, lines.size, Random.nextInt(1000))
    }

    // Cada toque reinicia el reloj: no cambia el mensaje justo después de que la persona lo cambió.
    LaunchedEffect(mood, touches, autoRotate) {
        if (!autoRotate || lines.size < 2) return@LaunchedEffect
        while (true) {
            delay(MascotTalk.ROTATE_MS)
            advance()
        }
    }

    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Mascot(
            pose = mood.pose,
            size = 96.dp,
            onClick = {
                touches++
                advance()
            },
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
                        touches++
                        advance()
                    }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(greeting, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                AnimatedContent(
                    targetState = index,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 4 }) togetherWith
                            fadeOut(tween(120))
                    },
                    label = "mascotLine",
                ) { i ->
                    val line = lines[i.coerceIn(0, lines.lastIndex)]
                    val action = line.action
                    Column {
                        Text(
                            stringResource(line.textRes, number),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (action != null) {
                            TextButton(onClick = { onAction(action) }) {
                                Text(stringResource(action.labelRes))
                            }
                        }
                    }
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
