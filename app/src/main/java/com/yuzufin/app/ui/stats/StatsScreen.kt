package com.yuzufin.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.BudgetLevel
import com.yuzufin.app.domain.BudgetProgress
import com.yuzufin.app.domain.MonthPoint
import com.yuzufin.app.domain.Recommendation
import com.yuzufin.app.ui.common.categoryIcon
import com.yuzufin.app.ui.common.formatMonthYear
import com.yuzufin.app.ui.components.IconBadge
import com.yuzufin.app.ui.components.ScreenHeader
import com.yuzufin.app.ui.components.YuzuCard
import com.yuzufin.app.ui.theme.yuzu
import java.util.Locale

@Composable
fun StatsScreen(
    contentPadding: PaddingValues,
    state: StatsUiState,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onManageBudgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.menu_stats), onBack = onBack)
        if (state.loading) return@Column
        val side = Modifier.padding(horizontal = 16.dp)

        MonthSelector(state, onPreviousMonth, onNextMonth, side)
        SummaryCard(state, side)
        ChartCard(state, side)
        CategoriesCard(state, side)
        BudgetsCard(state, onManageBudgets, side)
        RecommendationsCard(state, side)
        ExtrasCard(state, side)

        Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 16.dp))
    }
}

@Composable
private fun MonthSelector(state: StatsUiState, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.stats_prev_month))
        }
        Text(
            text = formatMonthYear(state.month),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = !state.isCurrentMonth) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.stats_next_month))
        }
    }
}

@Composable
private fun SummaryCard(state: StatsUiState, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    YuzuCard(modifier = modifier) {
        Text(stringResource(R.string.stats_summary_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        AmountLine(stringResource(R.string.stats_income), state.point.income, state.currency, MaterialTheme.yuzu.income)
        Spacer(Modifier.height(6.dp))
        AmountLine(stringResource(R.string.stats_expense), state.point.expense, state.currency, MaterialTheme.yuzu.expense)
        Spacer(Modifier.height(6.dp))
        AmountLine(
            stringResource(R.string.stats_net),
            state.point.net,
            state.currency,
            if (state.point.net >= 0) MaterialTheme.yuzu.income else MaterialTheme.yuzu.expense,
        )
        Spacer(Modifier.height(10.dp))
        Text(deltaText(state), style = MaterialTheme.typography.bodyMedium)
        state.dailyAverage?.let {
            Text(
                stringResource(R.string.stats_daily_average, MoneyFormatter.format(it, state.currency)),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        state.projectedExpense?.let {
            Text(
                stringResource(R.string.stats_projection, MoneyFormatter.format(it, state.currency)),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        state.savingsRate?.let {
            Text(stringResource(R.string.stats_rate, it), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.stats_excluded_note), style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun deltaText(state: StatsUiState): String {
    val previous = state.previousPoint.expense
    val current = state.point.expense
    if (previous <= 0L) return stringResource(R.string.stats_delta_none)
    val percent = ((current - previous) * 100 / previous).toInt()
    return when {
        percent > 0 -> stringResource(R.string.stats_delta_up, percent)
        percent < 0 -> stringResource(R.string.stats_delta_down, -percent)
        else -> stringResource(R.string.stats_delta_same)
    }
}

@Composable
private fun AmountLine(label: String, amount: Long, currency: CurrencyInfo, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(MoneyFormatter.format(amount, currency), style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun ChartCard(state: StatsUiState, modifier: Modifier) {
    val incomeColor = MaterialTheme.yuzu.income
    val expenseColor = MaterialTheme.yuzu.expense
    val track = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(R.string.stats_chart_desc)
    YuzuCard(modifier = modifier) {
        Text(stringResource(R.string.stats_chart_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(incomeColor, stringResource(R.string.stats_income))
            Legend(expenseColor, stringResource(R.string.stats_expense))
        }
        Spacer(Modifier.height(12.dp))
        val points = state.series
        val maxValue = (points.maxOfOrNull { maxOf(it.income, it.expense) } ?: 0L).coerceAtLeast(1L)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics { contentDescription = description },
        ) {
            if (points.isEmpty()) return@Canvas
            val slot = size.width / points.size
            val barWidth = slot * 0.26f
            val gap = slot * 0.06f
            val radius = CornerRadius(barWidth / 3f, barWidth / 3f)
            drawLine(track, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
            points.forEachIndexed { index, p ->
                val centre = slot * index + slot / 2f
                fun bar(value: Long, color: Color, left: Float) {
                    if (value <= 0L) return
                    val h = (size.height * (value.toDouble() / maxValue)).toFloat().coerceAtLeast(4f)
                    drawRoundRect(color, Offset(left, size.height - h), Size(barWidth, h), radius)
                }
                bar(p.income, incomeColor, centre - gap / 2f - barWidth)
                bar(p.expense, expenseColor, centre + gap / 2f)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { p ->
                Text(
                    text = monthLabel(p),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun monthLabel(point: MonthPoint): String =
    point.month.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.forLanguageTag("es"))
        .replaceFirstChar { it.uppercase() }

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CategoriesCard(state: StatsUiState, modifier: Modifier) {
    YuzuCard(modifier = modifier) {
        Text(stringResource(R.string.stats_categories_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (state.categoryRows.isEmpty()) {
            Text(
                stringResource(R.string.stats_categories_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@YuzuCard
        }
        state.categoryRows.forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(icon = categoryIcon(row.category?.iconKey.orEmpty()), size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.category?.name ?: stringResource(R.string.stats_unknown_category),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            MoneyFormatter.format(row.change.currentMinor, state.currency),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(fraction = row.sharePercent / 100f, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text(
                            "${row.sharePercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        val percent = row.change.deltaPercent
                        val text = when {
                            row.change.previousMinor <= 0L -> stringResource(R.string.stats_short_new)
                            percent == null || percent == 0 -> stringResource(R.string.stats_short_same)
                            percent > 0 -> stringResource(R.string.stats_short_up, percent)
                            else -> stringResource(R.string.stats_short_down, -percent)
                        }
                        val color = when {
                            percent == null || percent == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                            percent > 0 -> MaterialTheme.yuzu.expense
                            else -> MaterialTheme.yuzu.income
                        }
                        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.stats_categories_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0.01f, 1f))
                .clip(shape)
                .background(color),
        )
    }
}

@Composable
internal fun budgetColor(level: BudgetLevel): Color = when (level) {
    BudgetLevel.OK -> MaterialTheme.colorScheme.primary
    BudgetLevel.WARNING -> MaterialTheme.colorScheme.tertiary
    BudgetLevel.OVER -> MaterialTheme.yuzu.expense
}

@Composable
private fun BudgetsCard(state: StatsUiState, onManage: () -> Unit, modifier: Modifier) {
    YuzuCard(modifier = modifier) {
        Text(stringResource(R.string.stats_budgets_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (state.budgets.isEmpty()) {
            Text(
                stringResource(R.string.stats_budgets_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.budgets.forEachIndexed { index, progress ->
                if (index > 0) Spacer(Modifier.height(14.dp))
                BudgetLine(progress, state)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.stats_budgets_manage))
        }
    }
}

@Composable
private fun BudgetLine(progress: BudgetProgress, state: StatsUiState) {
    val color = budgetColor(progress.level)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            state.categoryNames[progress.categoryId] ?: stringResource(R.string.stats_unknown_category),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(
                R.string.stats_budget_progress,
                MoneyFormatter.format(progress.spentMinor, state.currency),
                MoneyFormatter.format(progress.limitMinor, state.currency),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Spacer(Modifier.height(6.dp))
    ProgressBar(fraction = progress.fraction, color = color)
    Spacer(Modifier.height(4.dp))
    Text(
        text = if (progress.remainingMinor >= 0) {
            stringResource(R.string.stats_budget_left, MoneyFormatter.format(progress.remainingMinor, state.currency))
        } else {
            stringResource(R.string.stats_budget_over, MoneyFormatter.format(-progress.remainingMinor, state.currency))
        },
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

@Composable
private fun RecommendationsCard(state: StatsUiState, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    YuzuCard(modifier = modifier) {
        Text(stringResource(R.string.stats_reco_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (state.recommendations.isEmpty()) {
            Text(stringResource(R.string.stats_reco_empty), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        state.recommendations.forEachIndexed { index, rec ->
            if (index > 0) Spacer(Modifier.height(14.dp))
            val (title, how) = recommendationText(rec, state)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.stats_reco_how, how),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                fontStyle = FontStyle.Italic,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.stats_reco_note), style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun recommendationText(rec: Recommendation, state: StatsUiState): Pair<String, String> {
    fun money(value: Long) = MoneyFormatter.format(value, state.currency)
    @Composable fun name(id: Long) = state.categoryNames[id] ?: stringResource(R.string.stats_unknown_category)
    return when (rec) {
        is Recommendation.BudgetOver -> stringResource(R.string.reco_budget_over_title, name(rec.categoryId)) to
            stringResource(R.string.reco_budget_over_how, money(rec.spentMinor), money(rec.limitMinor), money(rec.spentMinor - rec.limitMinor))
        is Recommendation.BudgetNear -> stringResource(R.string.reco_budget_near_title, name(rec.categoryId), rec.percent) to
            stringResource(R.string.reco_budget_near_how, money(rec.remainingMinor))
        is Recommendation.Deficit -> stringResource(R.string.reco_deficit_title) to
            stringResource(R.string.reco_deficit_how, money(rec.incomeMinor), money(rec.expenseMinor), money(rec.expenseMinor - rec.incomeMinor))
        is Recommendation.ProjectionAboveIncome -> stringResource(R.string.reco_projection_title) to
            stringResource(R.string.reco_projection_how, money(rec.projectedMinor), money(rec.incomeMinor))
        is Recommendation.CategoryRise -> stringResource(R.string.reco_rise_title, name(rec.categoryId)) to
            stringResource(R.string.reco_rise_how, name(rec.categoryId), money(rec.currentMinor), money(rec.previousMinor), rec.percent)
        is Recommendation.LowSavingsRate -> stringResource(R.string.reco_rate_title, rec.percent) to
            stringResource(R.string.reco_rate_how, rec.percent)
        is Recommendation.Scenario -> stringResource(
            R.string.reco_scenario_title,
            rec.percent,
            name(rec.categoryId),
            money(rec.savingMinor),
        ) to if (rec.monthsUsed > 0) {
            stringResource(R.string.reco_scenario_how_history, rec.monthsUsed, money(rec.averageMinor), rec.percent, money(rec.savingMinor))
        } else {
            stringResource(R.string.reco_scenario_how_current, money(rec.averageMinor), rec.percent, money(rec.savingMinor))
        }
    }
}

@Composable
private fun ExtrasCard(state: StatsUiState, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    YuzuCard(modifier = modifier) {
        AmountLine(stringResource(R.string.stats_saved_title), state.totalSaved, state.currency, MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        AmountLine(stringResource(R.string.stats_committed_title), state.committedNext30, state.currency, MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.stats_committed_note), style = MaterialTheme.typography.bodySmall, color = muted)
    }
}
