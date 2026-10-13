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
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.ui.unit.sp
import com.yuzufin.app.R
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.data.demo.DemoData
import com.yuzufin.app.data.demo.DemoData.Category
import com.yuzufin.app.data.demo.DemoData.MovementKind
import com.yuzufin.app.ui.components.DemoBanner
import com.yuzufin.app.ui.components.IconBadge
import com.yuzufin.app.ui.components.PillLabel
import com.yuzufin.app.ui.components.QuickAction
import com.yuzufin.app.ui.components.SectionHeader
import com.yuzufin.app.ui.components.YuzuCard
import com.yuzufin.app.ui.navigation.Destination
import com.yuzufin.app.ui.theme.Forest
import com.yuzufin.app.ui.theme.MintTile
import com.yuzufin.app.ui.theme.yuzu

/**
 * Pantalla de Inicio. En la Fase 1 todos los importes salen de [DemoData] y la pantalla
 * lo avisa con un banner. Orden: saldo → patrimonio → atajos → próximo cobro →
 * resumen del mes → vencimientos → movimientos recientes.
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onNavigate: (Destination) -> Unit,
    onQuickAction: (QuickAction) -> Unit,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        HomeHeader(onNotificationsClick)
        DemoBanner()
        BalanceCard(
            visible = balanceVisible,
            onToggleVisible = { balanceVisible = !balanceVisible },
            onGoMovements = { onNavigate(Destination.Movements) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                icon = Icons.Rounded.Savings,
                label = stringResource(R.string.home_saved),
                value = MoneyFormatter.format(DemoData.savedTotal, DemoData.currency),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Rounded.AccountBalance,
                label = stringResource(R.string.home_net_worth),
                value = MoneyFormatter.format(DemoData.netWorth, DemoData.currency),
                modifier = Modifier.weight(1f),
            )
        }
        ShortcutsCard(onQuickAction)
        NextIncomeBanner(onViewIncomes = { onNavigate(Destination.Incomes) })
        MonthSummaryCard()
        UpcomingBillsCard()
        RecentMovementsCard(onSeeAll = { onNavigate(Destination.Movements) })
        Spacer(Modifier.height(contentPadding.calculateBottomPadding()))
    }
}

@Composable
private fun HomeHeader(onNotificationsClick: () -> Unit) {
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
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
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
    visible: Boolean,
    onToggleVisible: () -> Unit,
    onGoMovements: () -> Unit,
) {
    val currency = DemoData.currency
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
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
                append(
                    if (visible) {
                        MoneyFormatter.formatNumber(DemoData.availableBalance, currency)
                    } else {
                        "••••••"
                    },
                )
            },
            style = MaterialTheme.typography.displaySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LabeledAmount(
                label = stringResource(R.string.home_committed),
                value = if (visible) MoneyFormatter.format(DemoData.committed, currency) else "${currency.symbol} ••••",
                color = MaterialTheme.yuzu.expense,
                modifier = Modifier.weight(1f),
            )
            LabeledAmount(
                label = stringResource(R.string.home_free),
                value = if (visible) MoneyFormatter.format(DemoData.freeEstimate, currency) else "${currency.symbol} ••••",
                color = MaterialTheme.yuzu.income,
                modifier = Modifier.weight(1f),
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
private fun LabeledAmount(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    YuzuCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        IconBadge(icon = icon, size = 40.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

@Composable
private fun NextIncomeBanner(onViewIncomes: () -> Unit) {
    val colors = MaterialTheme.yuzu
    val income = DemoData.nextIncome
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
                PillLabel(
                    text = stringResource(R.string.home_planned),
                    container = colors.onBanner.copy(alpha = 0.14f),
                    content = colors.onBanner,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.home_next_income),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onBanner,
                )
                Text(
                    text = "${income.name} · ${income.whenLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onBanner,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = MoneyFormatter.format(income.amount, DemoData.currency),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBanner,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onViewIncomes,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.strongButton,
                        contentColor = colors.onStrongButton,
                    ),
                ) {
                    Text(stringResource(R.string.home_view_incomes))
                }
            }
            Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
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
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

@Composable
private fun MonthSummaryCard() {
    val colors = MaterialTheme.yuzu
    val currency = DemoData.currency
    val income = DemoData.incomeThisMonth
    val expense = DemoData.expenseThisMonth
    // Proporción solo para dibujar la barra y el porcentaje; los importes siguen siendo enteros.
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
private fun UpcomingBillsCard() {
    YuzuCard {
        Text(
            text = stringResource(R.string.home_bills_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        DemoData.upcomingBills.forEachIndexed { index, bill ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MovementRow(
                icon = iconFor(bill.category),
                title = bill.name,
                subtitle = "${stringResource(R.string.home_bill_pending)} · ${bill.dueLabel}",
                amount = MoneyFormatter.format(bill.amount, DemoData.currency),
                amountColor = MaterialTheme.colorScheme.onSurface,
                badgeContainer = MaterialTheme.colorScheme.tertiaryContainer,
                badgeTint = MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_bills_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentMovementsCard(onSeeAll: () -> Unit) {
    val colors = MaterialTheme.yuzu
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    YuzuCard {
        SectionHeader(
            title = stringResource(R.string.home_recent_title),
            actionLabel = stringResource(R.string.home_see_all),
            onAction = onSeeAll,
        )
        DemoData.recentMovements.forEachIndexed { index, movement ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val isTransfer = movement.kind == MovementKind.InternalTransfer
            MovementRow(
                icon = iconFor(movement.category),
                title = movement.title,
                // El ahorro es una transferencia interna: se muestra neutro y sin signo.
                subtitle = if (isTransfer) {
                    "${movement.detail} · ${stringResource(R.string.home_internal_transfer)}"
                } else {
                    movement.detail
                },
                amount = when (movement.kind) {
                    MovementKind.Income -> MoneyFormatter.format(movement.amount, DemoData.currency, showPlus = true)
                    MovementKind.Expense -> MoneyFormatter.format(-movement.amount, DemoData.currency)
                    MovementKind.InternalTransfer -> MoneyFormatter.format(movement.amount, DemoData.currency)
                },
                amountColor = when (movement.kind) {
                    MovementKind.Income -> colors.income
                    MovementKind.Expense -> colors.expense
                    MovementKind.InternalTransfer -> neutral
                },
            )
        }
    }
}

@Composable
private fun MovementRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    amount: String,
    amountColor: Color,
    modifier: Modifier = Modifier,
    badgeContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    badgeTint: Color = MaterialTheme.colorScheme.secondary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon = icon, size = 44.dp, container = badgeContainer, tint = badgeTint)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = amount,
            style = MaterialTheme.typography.titleSmall,
            color = amountColor,
            maxLines = 1,
        )
    }
}

private fun iconFor(category: Category): ImageVector = when (category) {
    Category.Food -> Icons.Rounded.Restaurant
    Category.Salary -> Icons.Rounded.Payments
    Category.Savings -> Icons.Rounded.Savings
    Category.Transport -> Icons.Rounded.DirectionsBus
    Category.Internet -> Icons.Rounded.Wifi
    Category.Rent -> Icons.Rounded.Home
    Category.Electricity -> Icons.Rounded.Bolt
}
