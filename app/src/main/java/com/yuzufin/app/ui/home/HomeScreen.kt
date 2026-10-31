package com.yuzufin.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.yuzufin.app.ui.common.CapFontScale
import com.yuzufin.app.ui.common.entrance
import com.yuzufin.app.ui.common.rememberCountUp
import com.yuzufin.app.ui.common.rememberHaptic
import com.yuzufin.app.ui.components.Mascot
import com.yuzufin.app.ui.components.MascotPose
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.sp
import com.yuzufin.app.R
import com.yuzufin.app.ui.components.HintCard
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.BillStatus
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
import com.yuzufin.app.ui.components.MoneaCard
import com.yuzufin.app.ui.navigation.Destination
import com.yuzufin.app.ui.theme.Forest
import com.yuzufin.app.ui.theme.MintTile
import com.yuzufin.app.ui.theme.monea

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
    unreadNotifications: Int,
    onNotificationsClick: () -> Unit,
    onEditMovement: (Long) -> Unit,
    onConfirmIncome: (occurrenceId: Long, amountMinor: Long) -> Unit,
    onSkipIncome: (occurrenceId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf<PendingIncome?>(null) }
    val haptic = rememberHaptic()
    var balanceVisible by rememberSaveable { mutableStateOf(true) }
    val headerTop = MaterialTheme.monea.headerTop
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
        HomeHeader(name = state.userName, unread = unreadNotifications, onNotificationsClick = onNotificationsClick)

        if (!state.loading) {
            Box(Modifier.entrance(0)) {
                BalanceCard(
                    state = state,
                    visible = balanceVisible,
                    onToggleVisible = { balanceVisible = !balanceVisible },
                    onGoMovements = { onNavigate(Destination.Movements) },
                    onGoSavings = { onNavigate(Destination.Savings) },
                )
            }
            // Lo primero: una sola cosa que requiere atención (o el monito si está todo en orden).
            if (state.attention.isNotEmpty()) {
                Box(Modifier.entrance(1)) {
                    AttentionCard(
                        state = state,
                        onConfirmIncome = { confirming = it },
                        onSkipIncome = { onSkipIncome(it.occurrenceId) },
                        onOpen = onNavigate,
                    )
                }
            } else if (state.hasMovements) {
                Box(Modifier.entrance(1)) { MascotCard(state) }
            }
            HintCard(
                hintId = "home_numbers",
                titleRes = R.string.hint_home_title,
                bodyRes = R.string.hint_home_body,
                pose = MascotPose.TIP,
            )
            Box(Modifier.entrance(2)) { ShortcutsCard(onQuickAction) }
            if (!state.hasMovements) {
                Box(Modifier.entrance(3)) {
                    EmptyBanner(onRegister = { onQuickAction(QuickAction.Expense) })
                }
            }
            Box(Modifier.entrance(4)) { MonthSummaryCard(state) }
            if (state.recent.isNotEmpty()) {
                Box(Modifier.entrance(5)) {
                    RecentMovementsCard(
                        state = state,
                        onSeeAll = { onNavigate(Destination.Movements) },
                        onEdit = onEditMovement,
                    )
                }
            }
            // Lo que viene, más abajo: previsiones que no hace falta resolver hoy.
            state.nextIncome?.let {
                Box(Modifier.entrance(6)) { NextIncomeCard(it, state, onClick = { onNavigate(Destination.Incomes) }) }
            }
            if (state.upcomingBills.isNotEmpty()) {
                Box(Modifier.entrance(7)) {
                    UpcomingBillsCard(state, onClick = { onNavigate(Destination.Obligations) })
                }
            }
        }
        Spacer(Modifier.height(contentPadding.calculateBottomPadding()))
    }

    confirming?.let { item ->
        ConfirmIncomeDialog(
            item = item,
            currency = state.currency,
            onConfirm = { amount ->
                haptic()
                onConfirmIncome(item.occurrenceId, amount)
                confirming = null
            },
            onDismiss = { confirming = null },
        )
    }
}

/**
 * Lo que conviene resolver ya. Muestra una sola cosa y, si hay más, un botón “Ver N más” que las
 * despliega. Los cobros se confirman acá mismo; las facturas y los gastos fijos abren su pantalla.
 */
@Composable
private fun AttentionCard(
    state: HomeUiState,
    onConfirmIncome: (PendingIncome) -> Unit,
    onSkipIncome: (PendingIncome) -> Unit,
    onOpen: (Destination) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val items = state.attention
    val shown = if (expanded) items else items.take(1)

    MoneaCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Mascot(MascotPose.NOTIFICATION, size = 56.dp)
            Text(
                stringResource(R.string.home_attention_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        shown.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            when (item) {
                is AttentionItem.IncomeToConfirm -> PendingIncomeRow(
                    item = item.income,
                    currency = state.currency,
                    onConfirm = { onConfirmIncome(item.income) },
                    onSkip = { onSkipIncome(item.income) },
                )
                is AttentionItem.BillDue -> AttentionBillRow(item, state) { onOpen(Destination.Obligations) }
                is AttentionItem.RecurringDue -> AttentionRecurringRow(item, state) { onOpen(Destination.Recurring) }
            }
        }
        if (items.size > 1) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    if (expanded) {
                        stringResource(R.string.home_attention_less)
                    } else {
                        stringResource(R.string.home_attention_more, items.size - 1)
                    },
                )
            }
        }
    }
}

@Composable
private fun AttentionBillRow(item: AttentionItem.BillDue, state: HomeUiState, onView: () -> Unit) {
    val overdue = item.status == BillStatus.OVERDUE
    val whenText = when {
        overdue -> stringResource(R.string.obl_pill_overdue)
        item.daysLeft <= 0 -> stringResource(R.string.home_attention_today)
        item.daysLeft == 1 -> stringResource(R.string.home_attention_tomorrow)
        else -> stringResource(R.string.home_attention_in_days, item.daysLeft)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.bill.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "$whenText · ${formatFullDate(item.bill.dueDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) MaterialTheme.monea.expense else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(MoneyFormatter.format(item.bill.amountMinor, state.currency), style = MaterialTheme.typography.titleSmall)
        FilledTonalButton(onClick = onView) { Text(stringResource(R.string.home_attention_view)) }
    }
}

@Composable
private fun AttentionRecurringRow(item: AttentionItem.RecurringDue, state: HomeUiState, onView: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.recurring.name, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.home_attention_recurring, formatFullDate(item.recurring.dueDate)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(MoneyFormatter.format(item.recurring.amountMinor, state.currency), style = MaterialTheme.typography.titleSmall)
        FilledTonalButton(onClick = onView) { Text(stringResource(R.string.home_attention_view)) }
    }
}

/** Próximo cobro previsto. Está marcado como previsión: no forma parte del saldo. */
@Composable
private fun NextIncomeCard(item: UpcomingIncome, state: HomeUiState, onClick: () -> Unit) {
    MoneaCard(modifier = Modifier.clickable(onClick = onClick)) {
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

/** Próximos vencimientos: pendientes que todavía no descuentan el saldo. */
@Composable
private fun UpcomingBillsCard(state: HomeUiState, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    MoneaCard(modifier = Modifier.clickable(onClick = onClick)) {
        Text(stringResource(R.string.home_bills_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        state.upcomingBills.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.bill.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        formatFullDate(item.bill.dueDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.status == BillStatus.OVERDUE) MaterialTheme.monea.expense else muted,
                    )
                }
                if (item.status == BillStatus.OVERDUE) {
                    PillLabel(
                        text = stringResource(R.string.obl_pill_overdue),
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Text(MoneyFormatter.format(item.bill.amountMinor, state.currency), style = MaterialTheme.typography.titleSmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_bills_note), style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun HomeHeader(name: String, unread: Int, onNotificationsClick: () -> Unit) {
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
                BadgedBox(
                    badge = {
                        if (unread > 0) {
                            Badge { Text(if (unread > 9) "9+" else unread.toString()) }
                        }
                    },
                ) {
                    Icon(
                        Icons.Rounded.Notifications,
                        contentDescription = stringResource(R.string.home_notifications),
                    )
                }
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
    onGoSavings: () -> Unit,
) {
    val currency = state.currency
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val negative = state.balance < 0
    val shownBalance = rememberCountUp(state.balance)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BankCard(state = state, visible = visible, shownBalance = shownBalance, onToggleVisible = onToggleVisible)

        if (negative) {
            Text(
                text = stringResource(
                    if (state.saved > 0L) R.string.home_reserved_warning else R.string.home_negative_warning,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.monea.expense,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (state.saved > 0L) {
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(role = Role.Button, onClick = onGoSavings)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Rounded.Savings,
                    contentDescription = null,
                    tint = MaterialTheme.monea.income,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.home_saved) + ": " +
                        if (visible) MoneyFormatter.format(state.saved, currency) else "••••••",
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
            }
        }

        if (state.committed > 0L) {
            Text(
                text = stringResource(R.string.home_free) + ": " +
                    if (visible) MoneyFormatter.format(state.freeEstimate, currency) else "••••••",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.freeEstimate < 0) MaterialTheme.monea.expense else muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

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

/**
 * El saldo disponible dibujado como una tarjeta bancaria: "MoneaCard", con chip, símbolo de pago sin
 * contacto, el nombre de la persona y un brillo que la recorre de vez en cuando. Usa los colores del tema.
 */
@Composable
private fun BankCard(state: HomeUiState, visible: Boolean, shownBalance: Long, onToggleVisible: () -> Unit) {
    // La tarjeta tiene proporción fija: con letra enorme el texto se saldría, así que acá el tope es 1,15.
    CapFontScale(max = 1.15f) { BankCardContent(state, visible, shownBalance, onToggleVisible) }
}

@Composable
private fun BankCardContent(state: HomeUiState, visible: Boolean, shownBalance: Long, onToggleVisible: () -> Unit) {
    val colors = MaterialTheme.monea
    val ink = colors.onBanner
    val shape = RoundedCornerShape(24.dp)
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
    val amountText = if (visible) MoneyFormatter.formatNumber(shownBalance, state.currency) else "••••••"
    val holder = state.userName.trim().ifBlank { stringResource(R.string.bank_card_holder_default) }.uppercase()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.bannerStart, colors.bannerEnd))),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(Color.White.copy(alpha = 0.16f), radius = h * 0.80f, center = Offset(w * 0.98f, -h * 0.08f))
            drawCircle(Color.White.copy(alpha = 0.10f), radius = h * 0.55f, center = Offset(w * 0.04f, h * 1.08f))
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
                .fillMaxSize()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "Monea",
                        color = ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "Card",
                        color = ink.copy(alpha = 0.80f),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(start = 3.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onToggleVisible),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = stringResource(
                            if (visible) R.string.home_hide_balance else R.string.home_show_balance,
                        ),
                        tint = ink,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CardChip()
                ContactlessMark(ink)
            }

            Column(modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    text = stringResource(R.string.home_balance_label),
                    color = ink.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium)) {
                            append("${state.currency.symbol} ")
                        }
                        append(amountText)
                    },
                    color = ink,
                    fontSize = if (amountText.length > 13) 24.sp else 30.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = holder,
                    color = ink.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.6.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                PaymentMark()
            }
        }
    }
}

/** El chip dorado de las tarjetas. */
@Composable
private fun CardChip() {
    Canvas(modifier = Modifier.size(width = 46.dp, height = 34.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFFF3DE96), Color(0xFFC9A24A))),
            cornerRadius = CornerRadius(7.dp.toPx()),
        )
        val line = Color(0xFF7A5E22).copy(alpha = 0.55f)
        val stroke = 1.dp.toPx()
        drawLine(line, Offset(0f, h / 2f), Offset(w, h / 2f), stroke)
        drawLine(line, Offset(w / 3f, 0f), Offset(w / 3f, h), stroke)
        drawLine(line, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), stroke)
        drawRoundRect(
            color = line,
            topLeft = Offset(w / 3f, h * 0.25f),
            size = Size(w / 3f, h * 0.5f),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke),
        )
    }
}

/** Las ondas del pago sin contacto. */
@Composable
private fun ContactlessMark(color: Color) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        listOf(0.38f, 0.68f, 0.98f).forEach { fraction ->
            val radius = size.minDimension * fraction
            drawArc(
                color = color.copy(alpha = 0.85f),
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(-radius, size.height / 2f - radius),
                size = Size(radius * 2f, radius * 2f),
                style = stroke,
            )
        }
    }
}

/** Dos círculos que se cruzan, como el símbolo de las redes de pago (diseño propio de Monea). */
@Composable
private fun PaymentMark() {
    Canvas(modifier = Modifier.size(width = 54.dp, height = 33.dp)) {
        val r = size.height / 2f
        drawCircle(Color(0xFF1B8A4C).copy(alpha = 0.92f), radius = r, center = Offset(r, r))
        drawCircle(Color(0xFFF4C430).copy(alpha = 0.92f), radius = r, center = Offset(r + r * 1.25f, r))
    }
}

@Composable
private fun ShortcutsCard(onQuickAction: (QuickAction) -> Unit) {
    MoneaCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 20.dp)) {
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
            QuickAction.shortcuts.forEach { action ->
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
    val colors = MaterialTheme.monea
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
            Mascot(MascotPose.PLANNING, size = 110.dp)
        }
    }
}

/** El monito saluda según la hora y comenta cómo viene el mes. */
@Composable
private fun MascotCard(state: HomeUiState) {
    val hour = remember { java.time.LocalTime.now().hour }
    val suffix = if (state.userName.isBlank()) "" else ", ${state.userName.trim()}"
    val greeting = stringResource(
        when (hour) {
            in 5..11 -> R.string.mascot_greet_morning
            in 12..19 -> R.string.mascot_greet_afternoon
            else -> R.string.mascot_greet_night
        },
        suffix,
    )
    val daysAway = rememberAppContainer().sessionInfo.daysAway
    val comfortable = state.balance > 0 && state.monthIncome > 0 && state.monthExpense * 10 <= state.monthIncome * 7
    val pose: MascotPose
    val message: String
    when {
        daysAway >= 7 -> {
            pose = MascotPose.MISSED_YOU
            message = stringResource(R.string.mascot_msg_away, daysAway)
        }
        state.balance < 0 -> {
            pose = MascotPose.BROKE
            message = stringResource(R.string.mascot_msg_negative)
        }
        state.pendingIncomes.isNotEmpty() -> {
            pose = MascotPose.NOTIFICATION
            message = stringResource(R.string.mascot_msg_pending)
        }
        state.monthIncome == 0L && state.monthExpense == 0L -> {
            pose = MascotPose.WELCOME
            message = stringResource(R.string.mascot_msg_empty_month)
        }
        state.monthExpense > state.monthIncome -> {
            pose = MascotPose.OVERSPENT
            message = stringResource(R.string.mascot_msg_careful)
        }
        comfortable -> {
            pose = MascotPose.RICH
            message = stringResource(R.string.mascot_msg_rich)
        }
        else -> {
            pose = MascotPose.PROGRESS
            message = stringResource(R.string.mascot_msg_good)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Mascot(pose, size = 96.dp)
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(greeting, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonthSummaryCard(state: HomeUiState) {
    val colors = MaterialTheme.monea
    val currency = state.currency
    val income = state.monthIncome
    val expense = state.monthExpense
    val difference = income - expense
    // La proporción solo sirve para dibujar la barra; los importes son enteros.
    val fraction = if (income > 0) (expense.toFloat() / income.toFloat()).coerceIn(0f, 1f) else 0f
    val over = income > 0 && expense > income

    MoneaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.home_month_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (income > 0) {
                Text(
                    text = if (difference >= 0) {
                        stringResource(R.string.home_month_left, MoneyFormatter.format(difference, currency))
                    } else {
                        stringResource(R.string.home_month_over, MoneyFormatter.format(-difference, currency))
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (difference >= 0) colors.income else colors.expense,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (income > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(CircleShape)
                        .background(if (over) MaterialTheme.colorScheme.error else colors.expense),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.home_month_spent_of,
                    MoneyFormatter.format(expense, currency),
                    MoneyFormatter.format(income, currency),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.home_no_income_month),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expense > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_month_spent, MoneyFormatter.format(expense, currency)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun RecentMovementsCard(
    state: HomeUiState,
    onSeeAll: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    MoneaCard {
        SectionHeader(
            title = stringResource(R.string.home_recent_title),
            actionLabel = stringResource(R.string.home_see_all),
            onAction = onSeeAll,
        )
        state.recent.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            RecentRow(item = item, state = state, onClick = { if (!item.isInternal) onEdit(item.id) })
        }
    }
}

@Composable
private fun RecentRow(item: MovementItem, state: HomeUiState, onClick: () -> Unit) {
    val colors = MaterialTheme.monea
    val isIncome = item.type == TransactionType.INCOME
    val subtitle = if (item.isInternal) {
        "${stringResource(R.string.home_internal_transfer)} · ${dayLabel(item.date)}"
    } else if (item.description.isBlank()) {
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
