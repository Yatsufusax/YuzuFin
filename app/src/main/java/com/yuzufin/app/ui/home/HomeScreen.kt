package com.yuzufin.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import com.yuzufin.app.ui.common.entrance
import com.yuzufin.app.ui.common.rememberHaptic
import com.yuzufin.app.ui.components.Mascot
import com.yuzufin.app.ui.components.MascotMood
import com.yuzufin.app.ui.components.mascotForCurrency
import com.yuzufin.app.ui.components.MascotPose
import com.yuzufin.app.ui.components.MascotTalk
import com.yuzufin.app.ui.components.TalkingMascot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import com.yuzufin.app.R
import com.yuzufin.app.ui.assistant.MonkiAvatar
import java.time.YearMonth
import com.yuzufin.app.ui.assistant.MonkiIntroCard
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
    onMonkiClick: () -> Unit,
    onEditMovement: (Long) -> Unit,
    onConfirmIncome: (occurrenceId: Long, amountMinor: Long) -> Unit,
    onSkipIncome: (occurrenceId: Long) -> Unit,
    onBalanceHiddenChange: (Boolean) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf<PendingIncome?>(null) }
    val haptic = rememberHaptic()
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
        HomeHeader(
            currencyCode = state.currency.code,
            unread = unreadNotifications,
            onNotificationsClick = onNotificationsClick,
            onMonkiClick = onMonkiClick,
        )

        if (!state.loading) {
            // Presentación de Monki: una sola vez, a quien empieza y a quien se actualiza.
            MonkiIntroCard(currencyCode = state.currency.code, onOpen = onMonkiClick)
            Box(Modifier.entrance(0)) {
                BalanceCard(
                    state = state,
                    visible = !state.balanceHidden,
                    onToggleVisible = { onBalanceHiddenChange(!state.balanceHidden) },
                    onMonthChange = onMonthChange,
                    onGoStats = { onNavigate(Destination.Stats) },
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
private fun HomeHeader(currencyCode: String, unread: Int, onNotificationsClick: () -> Unit, onMonkiClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Entrada a Monki AI: su carita (según la moneda) + "Monki". El nombre de la persona está en la tarjeta.
        val openMonki = stringResource(R.string.monki_open)
        Surface(
            onClick = onMonkiClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.semantics { contentDescription = openMonki },
        ) {
            Row(
                modifier = Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MonkiAvatar(currencyCode = currencyCode, size = 40.dp)
                Text(
                    text = stringResource(R.string.monki_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
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
    onMonthChange: (YearMonth) -> Unit,
    onGoStats: () -> Unit,
    onGoSavings: () -> Unit,
) {
    val currency = state.currency
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryCard(
            state = state,
            visible = visible,
            onToggleVisible = onToggleVisible,
            onMonthChange = onMonthChange,
            onOpenStats = onGoStats,
        )

        if (state.balance < 0) {
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

        // Libre estimado: tu saldo disponible menos las facturas pendientes. Si no hay facturas
        // pendientes, es igual al saldo disponible; por eso se muestra siempre.
        Text(
            text = stringResource(R.string.home_free) + ": " +
                if (visible) MoneyFormatter.format(state.freeEstimate, currency) else "••••••",
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.freeEstimate < 0) MaterialTheme.monea.expense else muted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
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

/**
 * El monito saluda según la hora y comenta cómo viene el mes. Sus mensajes se van
 * tipeando solos, uno tras otro, y el monito se anima sin que lo toquen.
 */
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
    val mood = when {
        daysAway >= 7 -> MascotMood.AWAY
        state.balance < 0 -> MascotMood.NEGATIVE
        state.pendingIncomes.isNotEmpty() -> MascotMood.PENDING
        state.monthIncome == 0L && state.monthExpense == 0L -> MascotMood.EMPTY
        state.monthExpense > state.monthIncome -> MascotMood.CAREFUL
        comfortable -> MascotMood.RICH
        else -> MascotMood.GOOD
    }
    val number = if (mood == MascotMood.AWAY) {
        daysAway
    } else {
        MascotTalk.spentPercent(state.monthExpense, state.monthIncome)
    }
    TalkingMascot(
        mood = mood,
        greeting = greeting,
        number = number,
        // Cuando todo viene normal, el monito toma la bebida de tu moneda (tereré, mate o café).
        pose = if (mood == MascotMood.GOOD) mascotForCurrency(state.currency.code) else mood.pose,
    )
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
