package com.yuzufin.app.ui.sections

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yuzufin.app.R
import com.yuzufin.app.ui.components.ComingSoonScreen

// Secciones que se implementan en fases posteriores. Cada una explica qué incluirá
// y no muestra datos inventados.

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
