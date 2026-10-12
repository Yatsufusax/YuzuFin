package com.yuzufin.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R

/** Acciones de registro rápido. [phase] indica en qué fase del plan se implementan. */
enum class QuickAction(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val phase: Int,
) {
    Income(R.string.shortcut_income, Icons.Rounded.ArrowDownward, 2),
    Expense(R.string.shortcut_expense, Icons.Rounded.ArrowUpward, 2),
    Pay(R.string.shortcut_pay, Icons.Rounded.Payments, 5),
    Loan(R.string.shortcut_loan, Icons.Rounded.SwapHoriz, 5),
    Save(R.string.shortcut_save, Icons.Rounded.Savings, 4),
}

/** Contenido de la hoja que abre el botón central “+”. */
@Composable
fun QuickActionsSheetContent(onSelect: (QuickAction) -> Unit) {
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.quick_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        QuickAction.entries.forEach { action ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = Role.Button) { onSelect(action) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IconBadge(icon = action.icon, size = 44.dp)
                Text(
                    text = stringResource(action.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
