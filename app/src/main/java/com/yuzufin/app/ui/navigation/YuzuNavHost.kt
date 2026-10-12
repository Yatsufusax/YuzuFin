package com.yuzufin.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.yuzufin.app.ui.components.QuickAction
import com.yuzufin.app.ui.home.HomeScreen
import com.yuzufin.app.ui.menu.MenuScreen
import com.yuzufin.app.ui.sections.IncomesScreen
import com.yuzufin.app.ui.sections.MovementsScreen
import com.yuzufin.app.ui.sections.ObligationsScreen
import com.yuzufin.app.ui.sections.SavingsScreen
import com.yuzufin.app.ui.sections.SettingsScreen
import com.yuzufin.app.ui.sections.StatsScreen

@Composable
fun YuzuNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    onQuickAction: (QuickAction) -> Unit,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val goBack: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = Destination.Home.route,
        modifier = modifier,
    ) {
        composable(Destination.Home.route) {
            HomeScreen(
                contentPadding = contentPadding,
                onNavigate = navController::navigateTo,
                onQuickAction = onQuickAction,
                onNotificationsClick = onNotificationsClick,
            )
        }
        composable(Destination.Movements.route) { MovementsScreen(contentPadding) }
        composable(Destination.Savings.route) { SavingsScreen(contentPadding) }
        composable(Destination.Menu.route) {
            MenuScreen(contentPadding = contentPadding, onNavigate = navController::navigateTo)
        }
        composable(Destination.Incomes.route) { IncomesScreen(contentPadding, goBack) }
        composable(Destination.Obligations.route) { ObligationsScreen(contentPadding, goBack) }
        composable(Destination.Stats.route) { StatsScreen(contentPadding, goBack) }
        composable(Destination.Settings.route) { SettingsScreen(contentPadding, goBack) }
    }
}
