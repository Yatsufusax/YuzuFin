package com.yuzufin.app.ui.sections

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yuzufin.app.R
import com.yuzufin.app.ui.components.ComingSoonScreen

// Secciones que se implementan en fases posteriores. Cada una explica qué incluirá
// y no muestra datos inventados.

@Composable
fun SavingsScreen(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    ComingSoonScreen(
        titleRes = R.string.nav_savings,
        introRes = R.string.savings_intro,
        featuresRes = R.array.savings_features,
        phaseRes = R.string.phase_4,
        icon = Icons.Rounded.Savings,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@Composable
fun IncomesScreen(contentPadding: PaddingValues, onBack: () -> Unit, modifier: Modifier = Modifier) {
    ComingSoonScreen(
        titleRes = R.string.menu_incomes,
        introRes = R.string.incomes_intro,
        featuresRes = R.array.incomes_features,
        phaseRes = R.string.phase_3,
        icon = Icons.Rounded.AccountBalanceWallet,
        contentPadding = contentPadding,
        modifier = modifier,
        onBack = onBack,
    )
}

@Composable
fun ObligationsScreen(contentPadding: PaddingValues, onBack: () -> Unit, modifier: Modifier = Modifier) {
    ComingSoonScreen(
        titleRes = R.string.menu_obligations,
        introRes = R.string.obligations_intro,
        featuresRes = R.array.obligations_features,
        phaseRes = R.string.phase_5,
        icon = Icons.Rounded.CalendarMonth,
        contentPadding = contentPadding,
        modifier = modifier,
        onBack = onBack,
    )
}

@Composable
fun StatsScreen(contentPadding: PaddingValues, onBack: () -> Unit, modifier: Modifier = Modifier) {
    ComingSoonScreen(
        titleRes = R.string.menu_stats,
        introRes = R.string.stats_intro,
        featuresRes = R.array.stats_features,
        phaseRes = R.string.phase_6,
        icon = Icons.Rounded.BarChart,
        contentPadding = contentPadding,
        modifier = modifier,
        onBack = onBack,
    )
}
