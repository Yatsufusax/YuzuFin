package com.yuzufin.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.yuzufin.app.domain.TransactionType
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.components.QuickAction
import com.yuzufin.app.ui.home.HomeScreen
import com.yuzufin.app.ui.home.HomeViewModel
import com.yuzufin.app.ui.menu.MenuScreen
import com.yuzufin.app.ui.movements.MovementsScreen
import com.yuzufin.app.ui.sections.IncomesScreen
import com.yuzufin.app.ui.sections.ObligationsScreen
import com.yuzufin.app.ui.sections.SavingsScreen
import com.yuzufin.app.ui.sections.StatsScreen
import com.yuzufin.app.ui.settings.SettingsScreen
import com.yuzufin.app.ui.transaction.TransactionFormScreen
import com.yuzufin.app.ui.transaction.TransactionFormViewModel
import com.yuzufin.app.ui.update.UpdateViewModel

@Composable
fun YuzuNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    updateViewModel: UpdateViewModel,
    onQuickAction: (QuickAction) -> Unit,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = rememberAppContainer()
    val goBack: () -> Unit = { navController.popBackStack() }
    val openForm: (Long, TransactionType) -> Unit = { id, type ->
        navController.navigate(TransactionRoute.create(id, type)) { launchSingleTop = true }
    }

    NavHost(
        navController = navController,
        startDestination = Destination.Home.route,
        modifier = modifier,
    ) {
        composable(Destination.Home.route) {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
            val state by homeViewModel.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                contentPadding = contentPadding,
                state = state,
                onNavigate = navController::navigateTo,
                onQuickAction = onQuickAction,
                onNotificationsClick = onNotificationsClick,
                onEditMovement = { id -> openForm(id, TransactionType.EXPENSE) },
            )
        }
        composable(Destination.Movements.route) {
            MovementsScreen(
                contentPadding = contentPadding,
                onEdit = { id -> openForm(id, TransactionType.EXPENSE) },
                onNewMovement = { openForm(0L, TransactionType.EXPENSE) },
            )
        }
        composable(Destination.Savings.route) { SavingsScreen(contentPadding) }
        composable(Destination.Menu.route) {
            MenuScreen(contentPadding = contentPadding, onNavigate = navController::navigateTo)
        }
        composable(Destination.Incomes.route) { IncomesScreen(contentPadding, goBack) }
        composable(Destination.Obligations.route) { ObligationsScreen(contentPadding, goBack) }
        composable(Destination.Stats.route) { StatsScreen(contentPadding, goBack) }
        composable(Destination.Settings.route) {
            SettingsScreen(
                contentPadding = contentPadding,
                onBack = goBack,
                updateViewModel = updateViewModel,
            )
        }
        composable(
            route = TransactionRoute.PATTERN,
            arguments = listOf(
                navArgument(TransactionRoute.ARG_ID) { type = NavType.LongType; defaultValue = 0L },
                navArgument(TransactionRoute.ARG_TYPE) { type = NavType.StringType; defaultValue = TransactionType.EXPENSE.name },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong(TransactionRoute.ARG_ID) ?: 0L
            val type = runCatching {
                TransactionType.valueOf(entry.arguments?.getString(TransactionRoute.ARG_TYPE).orEmpty())
            }.getOrDefault(TransactionType.EXPENSE)
            val formViewModel: TransactionFormViewModel = viewModel(
                key = "form-$id-${type.name}",
                factory = TransactionFormViewModel.factory(container, id, type),
            )
            TransactionFormScreen(viewModel = formViewModel, onClose = goBack)
        }
    }
}
