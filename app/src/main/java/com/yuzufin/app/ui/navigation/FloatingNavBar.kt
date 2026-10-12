package com.yuzufin.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R

private data class BarEntry(
    val destination: Destination,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val leftEntries = listOf(
    BarEntry(Destination.Home, R.string.nav_home, Icons.Rounded.Home),
    BarEntry(Destination.Movements, R.string.nav_movements, Icons.Rounded.SwapVert),
)

private val rightEntries = listOf(
    BarEntry(Destination.Savings, R.string.nav_savings, Icons.Rounded.Savings),
    BarEntry(Destination.Menu, R.string.nav_menu, Icons.Rounded.Menu),
)

/** Barra inferior flotante con botón central “+”, como en la referencia. */
@Composable
fun FloatingNavBar(
    currentRoute: String?,
    onNavigate: (Destination) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            leftEntries.forEach { entry ->
                NavItem(entry, isSelected(entry, currentRoute)) { onNavigate(entry.destination) }
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Surface(
                    onClick = onAddClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(58.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.nav_add),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }

            rightEntries.forEach { entry ->
                NavItem(entry, isSelected(entry, currentRoute)) { onNavigate(entry.destination) }
            }
        }
    }
}

private fun isSelected(entry: BarEntry, currentRoute: String?): Boolean =
    if (entry.destination == Destination.Menu) {
        isMenuGroupRoute(currentRoute)
    } else {
        currentRoute == entry.destination.route
    }

@Composable
private fun RowScope.NavItem(
    entry: BarEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = stringResource(entry.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
