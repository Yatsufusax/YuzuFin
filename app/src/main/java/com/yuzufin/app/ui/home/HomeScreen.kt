package com.yuzufin.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.sp
import com.yuzufin.app.R
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.TransactionType
import com.yuzufin.app.ui.common.MovementItem
import com.yuzufin.app.ui.common.categoryIcon
import com.yuzufin.app.ui.common.dayLabel
import com.yuzufin.app.ui.common.formatFullDate
import com.yuzufin.app.ui.components.PillLabel
import com.yuzufin.app.ui.incomes.ConfirmIncomeDialog
import com.yuzufin.app.ui.incomes.PendingIncome
import com.yuzufin.app.ui.incomes.PendingIncomeRow
import com.yuzufin.app.ui.incomes.UpcomingIncome
import com.yuzufin.app.ui.components.IconBadge
import com.yuzufin.app.ui.components.QuickAction
import com.yuzufin.app.ui.components.SectionHeader
import com.yuzufin.app.ui.components.YuzuCard
import com.yuzufin.app.ui.navigation.Destination
import com.yuzufin.app.ui.theme.Forest
import com.yuzufin.app.ui.theme.MintTile
import com.yuzufin.app.ui.theme.yuzu

/**
 * Pantalla de Inicio con datos reales: saldo disponible, atajos, resumen del mes y últimos
 * movimientos. Las tarjetas de ahorros, obligaciones y patrimonio llegan en las Fases 4 y 5.
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    state: HomeUiState,
    onNavigate: (Destination) -> Unit,
    onQuickAction: (QuickAction) -> Unit,
    onNotificationsClick: () -> Unit,
    onEditMovement: (Long) -> Unit,
    onConfirmIncome: (occurrenceId: Long, amountMinor: Long) -> Unit,
    onSkipIncome: (occurrenceId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf<PendingIncome?>(null) }
    var balanceVisible by rememberSaveable { mutableStateOf(true) }
    val headerTop = MaterialTheme.yuzu.headerTop
    val background = MaterialTheme.colorScheme.background

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(headerTop, background),
                        startY = 0f,
                        endY = 380.dp.toPx(),
                    ),
                )
            }
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        HomeHeader(name = state.userName, onNotificationsClick = onNotificationsClick)

        if (!state.loading) {
            BalanceCard(
                state = state,
                visible = balanceVisible,
                onToggleVisible = { balanceVisible = !balanceVisible },
                onGoMovements = { onNavigate(Destination.Movements) },
            )
            if (state.pendingIncomes.isNotEmpty()) {
                PendingIncomesCard(
                    state = state,
                    onConfirm = { confirming = it },
                    onSkip = { onSkipIncome(it.occurrenceId) },
                    onSeeAll = { onNavigate(Destination.Incomes) },
                )
            }
            ShortcutsCard(onQuickAction)
            if (!state.hasMovements) {
                EmptyBanner(onRegister = { onQuickAction(QuickAction.Expense) })
            }
            MonthSummaryCard(state)
            state.nextIncome?.let { NextIncomeCard(it, state, onClick = { onNavigate(Destination.Incomes) }) }
            if (state.recent.isNotEmpty()) {
                RecentMovementsCard(
                    state = state,
                    onSeeAll = { onNavigate(Destination.Movements) },
                    onEdit = onEditMovement,
                )
            }
        }
        Spacer(Modifier.height(contentPadding.calculateBottomPadding()))
    }

    confirming?.let { item ->
        ConfirmIncomeDialog(
            item = item,
            currency = state.currency,
            onConfirm = { amount ->
                onConfirmIncome(item.occurrenceId, amount)
                confirming = null
            },
            onDismiss = { confirming = null },
        )
    }
}

/** Cobros que llegaron y esperan confirmación: es lo primero que conviene resolver. */
@Composable
private fun PendingIncomesCard(
    state: HomeUiState,
    onConfirm: (PendingIncome) -> Unit,
    onSkip: (PendingIncome) -> Unit,
    onSeeAll: () -> Unit,
) {
    YuzuCard {
        SectionHeader(
            title = stringResource(R.string.incomes_pending_title),
            actionLabel = stringResource(R.string.home_see_all),
            onAction = onSeeAll,
        )
        Spacer(Modifier.height(8.dp))
        state.pendingIncomes.take(3).forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            PendingIncomeRow(
                item = item,
                currency = state.currency,
                onConfirm = { onConfirm(item) },
                onSkip = { onSkip(item) },
            )
        }
        if (state.pendingIncomes.size > 3) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.home_pending_more, state.pendingIncomes.size - 3),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Próximo cobro previsto. Está marcado como previsión: no forma parte del saldo. */
@Composable
private fun NextIncomeCard(item: UpcomingIncome, state: HomeUiState, onClick: () -> Unit) {
    YuzuCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.home_next_income_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            PillLabel(
                text = stringResource(R.string.pill_forecast),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.sourceName, style = MaterialTheme.typography.titleSmall)
                Text(
                    formatFullDate(item.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                MoneyFormatter.format(item.amountMinor, state.currency),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeHeader(name: String, onNotificationsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MintTile),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Forest,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = name.ifBlank { stringResource(R.string.app_name) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(
            onClick = onNotificationsClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Notifications,
                    contentDescription = stringResource(R.string.home_notifications),
                )
            }
        }
    }
}

@Composable
private fun BalanceCard(
    state: HomeUiState,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    onGoMovements: () -> Unit,
) {
    val currency = state.currency
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val negative = state.balance < 0
    val amountColor = if (negative) MaterialTheme.yuzu.expense else MaterialTheme.colorScheme.onSurface

    YuzuCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.home_balance_label),
                style = MaterialTheme.typography.titleSmall,
                color = muted,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleVisible) {
                Icon(
                    imageVector = if (visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = stringResource(
                        if (visible) R.string.home_hide_balance else R.string.home_show_balance,
                    ),
                    tint = muted,
                )
            }
        }

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 22.sp, color = muted)) { append("${currency.symbol} ") }
                append(if (visible) MoneyFormatter.formatNumber(state.balance, currency) else "••••••")
            },
            style = MaterialTheme.typography.displaySmall,
            color = amountColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (negative) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_negative_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.yuzu.expense,
            )
        }

        Spacer(Modifier.height(20.dp))
        FilledTonalButton(
            onClick = onGoMovements,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text(stringResource(R.string.home_go_movements))
        }
    }
}

@Composable
private fun ShortcutsCard(onQuickAction: (QuickAction) -> Unit) {
    YuzuCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 20.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Rounded.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(R.string.home_shortcuts),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            QuickAction.entries.forEach { action ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button) { onQuickAction(action) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconBadge(icon = action.icon, size = 52.dp)
                    Text(
                        text = stringResource(action.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Invitación a registrar el primer movimiento, con el estilo del banner verde de la referencia. */
@Composable
private fun EmptyBanner(onRegister: () -> Unit) {
    val colors = MaterialTheme.yuzu
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(colors.bannerStart, colors.bannerEnd))),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onBanner,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onBanner,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRegister,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.strongButton,
                        contentColor = colors.onStrongButton,
                    ),
                ) {
                    Text(stringResource(R.string.home_empty_action))
                }
            }
            Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    drawCircle(color = colors.onBanner.copy(alpha = 0.08f), radius = r)
                    drawCircle(color = colors.onBanner.copy(alpha = 0.10f), radius = r * 0.72f)
                    drawCircle(color = colors.onBanner.copy(alpha = 0.14f), radius = r * 0.44f)
                }
                Icon(
                    Icons.Rounded.Payments,
                    contentDescription = null,
                    tint = colors.onBanner,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

@Composable
private fun MonthSummaryCard(state: HomeUiState) {
    val colors = MaterialTheme.yuzu
    val currency = state.currency
    val income = state.monthIncome
    val expense = state.monthExpense
    // La proporción solo sirve para dibujar la barra y el porcentaje; los importes son enteros.
    val percent = if (income > 0) ((expense * 100) / income).toInt().coerceIn(0, 100) else 0

    YuzuCard {
        Text(
            text = stringResource(R.string.home_month_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MonthFigure(
                icon = Icons.Rounded.ArrowDownward,
                label = stringResource(R.string.home_income_real),
                value = MoneyFormatter.format(income, currency),
                color = colors.income,
                modifier = Modifier.weight(1f),
            )
            MonthFigure(
                icon = Icons.Rounded.ArrowUpward,
                label = stringResource(R.string.home_expense_real),
                value = MoneyFormatter.format(expense, currency),
                color = colors.expense,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        if (income > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(percent / 100f)
                        .clip(CircleShape)
                        .background(colors.expense),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_month_ratio, percent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.home_no_income_month),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.home_month_difference),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = MoneyFormatter.format(income - expense, currency, showPlus = true),
                style = MaterialTheme.typography.titleSmall,
                color = if (income - expense >= 0) colors.income else colors.expense,
            )
        }
    }
}

@Composable
private fun MonthFigure(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBadge(icon = icon, size = 36.dp, container = color.copy(alpha = 0.14f), tint = color)
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentMovementsCard(
    state: HomeUiState,
    onSeeAll: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    YuzuCard {
        SectionHeader(
            title = stringResource(R.string.home_recent_title),
            actionLabel = stringResource(R.string.home_see_all),
            onAction = onSeeAll,
        )
        state.recent.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            RecentRow(item = item, state = state, onClick = { onEdit(item.id) })
        }
    }
}

@Composable
private fun RecentRow(item: MovementItem, state: HomeUiState, onClick: () -> Unit) {
    val colors = MaterialTheme.yuzu
    val isIncome = item.type == TransactionType.INCOME
    val subtitle = if (item.description.isBlank()) {
        dayLabel(item.date)
    } else {
        "${item.categoryName} · ${dayLabel(item.date)}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon = categoryIcon(item.iconKey), size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = if (isIncome) {
                MoneyFormatter.format(item.amountMinor, state.currency, showPlus = true)
            } else {
                MoneyFormatter.format(-item.amountMinor, state.currency)
            },
            style = MaterialTheme.typography.titleSmall,
            color = if (isIncome) colors.income else colors.expense,
            maxLines = 1,
        )
    }
}
