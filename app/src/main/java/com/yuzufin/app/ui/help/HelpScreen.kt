package com.yuzufin.app.ui.help

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.components.Mascot
import com.yuzufin.app.ui.components.MascotPose
import com.yuzufin.app.ui.components.MoneaCard
import com.yuzufin.app.ui.components.ScreenHeader
import kotlinx.coroutines.launch

private data class HelpItem(@StringRes val question: Int, @StringRes val answer: Int)

private val helpItems = listOf(
    HelpItem(R.string.help_q_balance, R.string.help_a_balance),
    HelpItem(R.string.help_q_free, R.string.help_a_free),
    HelpItem(R.string.help_q_add, R.string.help_a_add),
    HelpItem(R.string.help_q_incomes, R.string.help_a_incomes),
    HelpItem(R.string.help_q_recurring, R.string.help_a_recurring),
    HelpItem(R.string.help_q_savings, R.string.help_a_savings),
    HelpItem(R.string.help_q_obligations, R.string.help_a_obligations),
    HelpItem(R.string.help_q_budgets, R.string.help_a_budgets),
    HelpItem(R.string.help_q_vouchers, R.string.help_a_vouchers),
    HelpItem(R.string.help_q_backup, R.string.help_a_backup),
    HelpItem(R.string.help_q_currency, R.string.help_a_currency),
    HelpItem(R.string.help_q_privacy, R.string.help_a_privacy),
)

/** Centro de ayuda: preguntas frecuentes que se abren y cierran, explicadas por el monito. */
@Composable
fun HelpScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = rememberAppContainer()
    val scope = rememberCoroutineScope()
    // -1 = todas cerradas. rememberSaveable: al rotar la pantalla sigue abierta la misma.
    var openIndex by rememberSaveable { mutableStateOf(-1) }
    var resetDone by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.help_title), onBack = onBack)

        MoneaCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Mascot(MascotPose.QUESTION, size = 96.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.help_intro_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.help_intro_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        helpItems.forEachIndexed { index, item ->
            val open = openIndex == index
            MoneaCard(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .animateContentSize(),
                contentPadding = PaddingValues(0.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { openIndex = if (open) -1 else index }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(item.question),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
                if (open) {
                    Text(
                        stringResource(item.answer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                    )
                }
            }
        }

        OutlinedButton(
            onClick = {
                scope.launch {
                    container.settingsRepository.resetHints()
                    resetDone = true
                }
            },
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        ) { Text(stringResource(R.string.help_reset_hints)) }
        if (resetDone) {
            Text(
                stringResource(R.string.help_reset_hints_done),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 16.dp))
    }
}
