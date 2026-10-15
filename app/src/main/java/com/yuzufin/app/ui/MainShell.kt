package com.yuzufin.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yuzufin.app.R
import com.yuzufin.app.domain.TransactionType
import com.yuzufin.app.reminders.ReminderScheduler
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.components.QuickAction
import com.yuzufin.app.ui.components.QuickActionsSheetContent
import com.yuzufin.app.ui.navigation.Destination
import com.yuzufin.app.ui.navigation.FloatingNavBar
import com.yuzufin.app.ui.navigation.TransactionRoute
import com.yuzufin.app.ui.navigation.YuzuNavHost
import com.yuzufin.app.ui.navigation.isFullScreenRoute
import com.yuzufin.app.ui.navigation.navigateTo
import com.yuzufin.app.ui.update.UpdateEffects
import com.yuzufin.app.ui.update.UpdateViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Estructura principal: barra flotante, navegación, avisos y hoja de registro rápido. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell() {
    val container = rememberAppContainer()
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showQuickSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val updateViewModel: UpdateViewModel = viewModel(factory = UpdateViewModel.factory(container))
    // Busca actualizaciones cada minuto mientras la app está a la vista.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { updateViewModel.pollLoop() }
    }
    UpdateEffects(updateViewModel)

    // Ajustes de recordatorios: programa (o cancela) el revisor diario de cobros.
    val appSettings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(appSettings?.remindersEnabled, appSettings?.reminderHour) {
        appSettings?.let { ReminderScheduler.apply(context, it.remindersEnabled, it.reminderHour) }
    }

    // Cada vez que se abre la app: crea los cobros vencidos (y registra los automáticos).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            try {
                container.incomeRepository.reconcile(LocalDate.now())
            } catch (e: Exception) {
                // Se reintenta la próxima vez; no se escribe ningún dato en el log.
            }
        }
    }

    // Pagar, préstamos y ahorro todavía no existen: se avisa con honestidad en qué fase llegan.
    val showComingSoon: (Int) -> Unit = { phase ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(context.getString(R.string.coming_soon_snackbar, phase))
        }
    }

    val onQuickAction: (QuickAction) -> Unit = { action ->
        when (action) {
            QuickAction.Income -> navController.navigate(TransactionRoute.create(type = TransactionType.INCOME))
            QuickAction.Expense -> navController.navigate(TransactionRoute.create(type = TransactionType.EXPENSE))
            QuickAction.Save -> navController.navigateTo(Destination.Savings)
            else -> showComingSoon(action.phase)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // El formulario ocupa toda la pantalla: sin barra flotante.
            if (!isFullScreenRoute(currentRoute)) {
                FloatingNavBar(
                    currentRoute = currentRoute,
                    onNavigate = navController::navigateTo,
                    onAddClick = { showQuickSheet = true },
                )
            }
        },
    ) { innerPadding ->
        YuzuNavHost(
            navController = navController,
            contentPadding = innerPadding,
            updateViewModel = updateViewModel,
            onQuickAction = onQuickAction,
            onNotificationsClick = { showComingSoon(3) },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showQuickSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuickSheet = false },
            sheetState = sheetState,
        ) {
            QuickActionsSheetContent(
                onSelect = { action ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) showQuickSheet = false
                    }
                    onQuickAction(action)
                },
            )
        }
    }
}
