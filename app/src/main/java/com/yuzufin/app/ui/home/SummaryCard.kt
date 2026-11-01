package com.yuzufin.app.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzufin.app.R
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.ui.common.CapFontScale
import com.yuzufin.app.ui.common.rememberCountUp
import com.yuzufin.app.ui.common.rememberFillAnimation
import com.yuzufin.app.ui.theme.monea
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToLong

/** Cuántos meses hacia atrás deja elegir el selector de la tarjeta. */
internal const val SUMMARY_MONTHS_BACK = 12

/** Cuánto del ingreso del mes se gastó, para dibujar la barra y el porcentaje. */
internal data class SummaryProgress(
    /** De 0 a 1, solo para el largo de la barra. */
    val fraction: Float,
    /** Porcentaje gastado, o null si no hubo ingresos (no se puede calcular). Tope 999. */
    val percent: Int?,
    /** Gastaste más de lo que ingresó. */
    val over: Boolean,
)

internal fun summaryProgress(income: Long, expense: Long): SummaryProgress {
    val over = expense > income
    return when {
        income > 0L -> SummaryProgress(
            fraction = (expense.toDouble() / income.toDouble()).coerceIn(0.0, 1.0).toFloat(),
            percent = (expense.toDouble() * 100.0 / income.toDouble()).roundToLong().coerceAtMost(999L).toInt(),
            over = over,
        )
        expense > 0L -> SummaryProgress(fraction = 1f, percent = null, over = true)
        else -> SummaryProgress(fraction = 0f, percent = null, over = false)
    }
}

/** Meses que ofrece el selector: del actual hacia atrás, sin pasar del primer mes con datos posibles. */
internal fun summaryMonthOptions(today: YearMonth, first: YearMonth): List<YearMonth> {
    val list = generateSequence(today) { it.minusMonths(1) }
        .take(SUMMARY_MONTHS_BACK)
        .takeWhile { !it.isBefore(first) }
        .toList()
    return list.ifEmpty { listOf(today) }
}

/** “Octubre”; con el año si no es el actual (“Octubre 2025”). */
internal fun summaryMonthLabel(month: YearMonth, today: YearMonth): String {
    val name = month.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"))
        .replaceFirstChar { it.uppercase() }
    return if (month.year == today.year) name else "$name ${month.year}"
}

/**
 * El número grande de la tarjeta: el dinero que la persona tiene disponible hoy (saldo menos lo
 * apartado en metas). NO depende del mes elegido y no vuelve a cero cuando empieza un mes; lo que
 * sí cambia con el mes son la barra, el porcentaje, los ingresos y los gastos.
 */
internal fun summaryHeadline(state: HomeUiState): Long = state.balance

/**
 * Tarjeta de resumen de Inicio: el presupuesto disponible (el dinero que hay hoy), cuánto del
 * ingreso del mes elegido se gastó, y los ingresos y gastos de ese mes. El mes se elige arriba.
 * Tiene el monito de la moneda a la derecha y el brillo que la recorre de vez en cuando.
 */
@Composable
internal fun SummaryCard(
    state: HomeUiState,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onOpenStats: () -> Unit,
) {
    // La tarjeta tiene proporciones pensadas: con letra enorme el texto se saldría, así que acá el tope es 1,15.
    CapFontScale(max = 1.15f) { SummaryCardContent(state, visible, onToggleVisible, onMonthChange, onOpenStats) }
}

@Composable
private fun SummaryCardContent(
    state: HomeUiState,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onOpenStats: () -> Unit,
) {
    val colors = MaterialTheme.monea
    val ink = colors.onBanner
    val shape = RoundedCornerShape(28.dp)
    val currency = state.currency
    val income = state.shownIncome
    val expense = state.shownExpense
    val progress = summaryProgress(income, expense)
    val fill = rememberFillAnimation(progress.fraction)
    val shownBalance = rememberCountUp(summaryHeadline(state))
    val hiddenDots = "••••••"
    val amountText = if (visible) MoneyFormatter.formatNumber(shownBalance, currency) else hiddenDots
    val percentText = progress.percent?.let { stringResource(R.string.summary_card_percent, it) }
        ?: stringResource(R.string.summary_card_percent_none)

    val transition = rememberInfiniteTransition(label = "sheen")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 5200
                0f at 0
                1f at 1600 using FastOutSlowInEasing
                1f at 5200
            },
        ),
        label = "sweep",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.bannerStart, colors.bannerEnd))),
    ) {
        // Círculos de adorno y el brillo que pasa de vez en cuando.
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            drawCircle(Color.White.copy(alpha = 0.14f), radius = h * 0.62f, center = Offset(w * 1.02f, h * 0.08f))
            drawCircle(Color.White.copy(alpha = 0.10f), radius = h * 0.50f, center = Offset(w * 0.96f, h * 1.02f))
            drawCircle(Color.White.copy(alpha = 0.07f), radius = h * 0.34f, center = Offset(w * 0.02f, h * 1.06f))
            val bandWidth = w * 0.26f
            val x = -w * 0.35f + sweep * w * 1.7f
            rotate(degrees = 20f, pivot = center) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                        startX = x,
                        endX = x + bandWidth,
                    ),
                    topLeft = Offset(x, -h),
                    size = Size(bandWidth, h * 3f),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 18.dp),
        ) {
            // Arriba: mes, ojo para tapar los importes y estadísticas.
            Row(verticalAlignment = Alignment.CenterVertically) {
                // El selector ocupa todo el ancho libre (antes compartía el espacio con un Spacer y el
                // nombre del mes se cortaba: "Resum…").
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    MonthPill(state = state, ink = ink, onMonthChange = onMonthChange)
                }
                CardIconButton(
                    icon = if (visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    description = stringResource(if (visible) R.string.home_hide_balance else R.string.home_show_balance),
                    ink = ink,
                    onClick = onToggleVisible,
                )
                CardIconButton(
                    icon = Icons.Rounded.BarChart,
                    description = stringResource(R.string.summary_card_open_stats),
                    ink = ink,
                    onClick = onOpenStats,
                )
            }

            // Centro: el número grande a la izquierda y el monito a la derecha.
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 128.dp)) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(0.62f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium)) {
                                append("${currency.symbol} ")
                            }
                            append(amountText)
                        },
                        color = ink,
                        fontSize = when {
                            amountText.length > 11 -> 22.sp
                            amountText.length > 9 -> 26.sp
                            else -> 32.sp
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.summary_card_budget),
                        color = ink.copy(alpha = 0.90f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(12.dp)
                                .clip(CircleShape)
                                .background(ink.copy(alpha = 0.20f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fill.coerceIn(0f, 1f))
                                    .clip(CircleShape)
                                    .background(if (progress.over) MaterialTheme.colorScheme.error else ink),
                            )
                        }
                        Text(
                            text = percentText,
                            color = ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
                CardMonkey(
                    currencyCode = currency.code,
                    ink = ink,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = ink.copy(alpha = 0.22f))
            Spacer(Modifier.height(12.dp))

            // Abajo: ingresos y gastos del mes elegido.
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowStat(
                    icon = Icons.Rounded.ArrowDownward,
                    label = stringResource(R.string.stats_income),
                    amount = if (visible) MoneyFormatter.format(income, currency) else hiddenDots,
                    ink = ink,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(38.dp)
                        .background(ink.copy(alpha = 0.22f)),
                )
                FlowStat(
                    icon = Icons.Rounded.ArrowUpward,
                    label = stringResource(R.string.stats_expense),
                    amount = if (visible) MoneyFormatter.format(expense, currency) else hiddenDots,
                    ink = ink,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                )
            }
        }
    }
}

/** Botón redondo de la esquina de la tarjeta, con la zona táctil de 48 dp. */
@Composable
private fun CardIconButton(icon: ImageVector, description: String, ink: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = ink, modifier = Modifier.size(22.dp))
    }
}

/** “Resumen de Octubre ⌄”: abre la lista de meses. */
@Composable
private fun MonthPill(
    state: HomeUiState,
    ink: Color,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val today = remember { YearMonth.now() }
    val options = remember(state.firstMonth) { summaryMonthOptions(today, state.firstMonth) }
    val pickLabel = stringResource(R.string.summary_card_pick_month)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = pickLabel) { open = true },
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.14f))
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.summary_card_title, summaryMonthLabel(state.selectedMonth, today)),
                    color = ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { month ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = summaryMonthLabel(month, today),
                            fontWeight = if (month == state.selectedMonth) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        open = false
                        onMonthChange(month)
                    },
                )
            }
        }
    }
}

/** Ingresos o gastos del mes: círculo con flecha, título y importe. */
@Composable
private fun FlowStat(icon: ImageVector, label: String, amount: String, ink: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ink.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = ink.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
            Text(
                text = amount,
                color = ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
