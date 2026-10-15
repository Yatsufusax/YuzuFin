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
import com.yuzufin.app.domain.LoanDirection
import com.yuzufin.app.domain.TransactionType
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.components.QuickAction
import com.yuzufin.app.ui.home.HomeScreen
import com.yuzufin.app.ui.home.HomeViewModel
import com.yuzufin.app.ui.menu.MenuScreen
import com.yuzufin.app.ui.movements.MovementsScreen
import com.yuzufin.app.ui.incomes.IncomeFormScreen
import com.yuzufin.app.ui.incomes.IncomeFormViewModel
import com.yuzufin.app.ui.incomes.IncomesScreen
import com.yuzufin.app.ui.obligations.BillFormScreen
import com.yuzufin.app.ui.obligations.BillFormViewModel
import com.yuzufin.app.ui.obligations.LoanFormScreen
import com.yuzufin.app.ui.obligations.LoanFormViewModel
import com.yuzufin.app.ui.obligations.ObligationsScreen
import com.yuzufin.app.ui.savings.SavingsGoalFormScreen
import com.yuzufin.app.ui.savings.SavingsGoalFormViewModel
import com.yuzufin.app.ui.savings.SavingsScreen
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
                onConfirmIncome = homeViewModel::confirmIncome,
                onSkipIncome = homeViewModel::skipIncome,
            )
        }
        composable(Destination.Movements.route) {
            MovementsScreen(
                contentPadding = contentPadding,
                onEdit = { id -> openForm(id, TransactionType.EXPENSE) },
                onNewMovement = { openForm(0L, TransactionType.EXPENSE) },
            )
        }
        composable(Destination.Savings.route) {
            SavingsScreen(
                contentPadding = contentPadding,
                onAddGoal = { navController.navigate(SavingsGoalFormRoute.create()) { launchSingleTop = true } },
                onEditGoal = { id -> navController.navigate(SavingsGoalFormRoute.create(id)) { launchSingleTop = true } },
            )
        }
        composable(Destination.Menu.route) {
            MenuScreen(contentPadding = contentPadding, onNavigate = navController::navigateTo)
        }
        composable(Destination.Incomes.route) {
            IncomesScreen(
                contentPadding = contentPadding,
                onBack = goBack,
                onAddSource = { navController.navigate(IncomeFormRoute.create()) { launchSingleTop = true } },
                onEditSource = { id -> navController.navigate(IncomeFormRoute.create(id)) { launchSingleTop = true } },
            )
        }
        composable(Destination.Obligations.route) {
            ObligationsScreen(
                contentPadding = contentPadding,
                onBack = goBack,
                onAddBill = { navController.navigate(BillFormRoute.create()) { launchSingleTop = true } },
                onEditBill = { id -> navController.navigate(BillFormRoute.create(id)) { launchSingleTop = true } },
                onAddLoan = { direction -> navController.navigate(LoanFormRoute.create(direction = direction)) { launchSingleTop = true } },
                onEditLoan = { id -> navController.navigate(LoanFormRoute.create(id)) { launchSingleTop = true } },
            )
        }
        composable(Destination.Stats.route) { StatsScreen(contentPadding, goBack) }
        composable(Destination.Settings.route) {
            SettingsScreen(
                contentPadding = contentPadding,
                onBack = goBack,
                updateViewModel = updateViewModel,
            )
        }
        composable(
            route = IncomeFormRoute.PATTERN,
            arguments = listOf(
                navArgument(IncomeFormRoute.ARG_ID) { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong(IncomeFormRoute.ARG_ID) ?: 0L
            val formViewModel: IncomeFormViewModel = viewModel(
                key = "income-form-$id",
                factory = IncomeFormViewModel.factory(container, id),
            )
            IncomeFormScreen(viewModel = formViewModel, onClose = goBack)
        }
        composable(
            route = SavingsGoalFormRoute.PATTERN,
            arguments = listOf(
                navArgument(SavingsGoalFormRoute.ARG_ID) { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong(SavingsGoalFormRoute.ARG_ID) ?: 0L
            val formViewModel: SavingsGoalFormViewModel = viewModel(
                key = "savings-goal-form-$id",
                factory = SavingsGoalFormViewModel.factory(container, id),
            )
            SavingsGoalFormScreen(viewModel = formViewModel, onClose = goBack)
        }
        composable(
            route = BillFormRoute.PATTERN,
            arguments = listOf(
                navArgument(BillFormRoute.ARG_ID) { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong(BillFormRoute.ARG_ID) ?: 0L
            val formViewModel: BillFormViewModel = viewModel(
                key = "bill-form-$id",
                factory = BillFormViewModel.factory(container, id),
            )
            BillFormScreen(viewModel = formViewModel, onClose = goBack)
        }
        composable(
            route = LoanFormRoute.PATTERN,
            arguments = listOf(
                navArgument(LoanFormRoute.ARG_ID) { type = NavType.LongType; defaultValue = 0L },
                navArgument(LoanFormRoute.ARG_DIRECTION) { type = NavType.StringType; defaultValue = LoanDirection.LENT.name },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong(LoanFormRoute.ARG_ID) ?: 0L
            val direction = runCatching {
                LoanDirection.valueOf(entry.arguments?.getString(LoanFormRoute.ARG_DIRECTION).orEmpty())
            }.getOrDefault(LoanDirection.LENT)
            val formViewModel: LoanFormViewModel = viewModel(
                key = "loan-form-$id-${direction.name}",
                factory = LoanFormViewModel.factory(container, id, direction),
            )
            LoanFormScreen(viewModel = formViewModel, onClose = goBack)
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
