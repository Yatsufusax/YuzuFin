package com.yuzufin.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yuzufin.app.domain.TransactionType
import com.yuzufin.app.reminders.HabitReminderScheduler
import com.yuzufin.app.reminders.ReminderScheduler
import com.yuzufin.app.reminders.WeeklySummaryScheduler
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.R
import com.yuzufin.app.ui.common.DeleteUndo
import com.yuzufin.app.ui.common.LocalDeleteUndo
import com.yuzufin.app.ui.components.QuickAction
import com.yuzufin.app.ui.navigation.BillFormRoute
import com.yuzufin.app.ui.navigation.Destination
import com.yuzufin.app.ui.navigation.FloatingNavBar
import com.yuzufin.app.ui.navigation.LoanFormRoute
import com.yuzufin.app.ui.navigation.QuickLaunch
import com.yuzufin.app.ui.navigation.SummaryLaunch
import com.yuzufin.app.ui.navigation.TransactionRoute
import com.yuzufin.app.ui.navigation.VoiceRoute
import kotlinx.coroutines.delay
import com.yuzufin.app.ui.voice.VoiceTipBubble
import com.yuzufin.app.ui.navigation.MoneaNavHost
import com.yuzufin.app.ui.navigation.isFullScreenRoute
import com.yuzufin.app.ui.navigation.navigateTo
import com.yuzufin.app.ui.tour.FirstExpenseCelebration
import com.yuzufin.app.ui.tour.FirstUseTour
import com.yuzufin.app.ui.update.UpdateEffects
import com.yuzufin.app.ui.update.UpdateViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Estructura principal: barra flotante, navegación, avisos y hoja de registro rápido. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    openExpense: Boolean = false,
    onOpenExpenseHandled: () -> Unit = {},
    openSummaryKey: String? = null,
    onSummaryHandled: () -> Unit = {},
) {
    val container = rememberAppContainer()
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val undoMessage = stringResource(R.string.undo_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val deleteUndo = remember(container, scope, snackbarHostState, undoMessage, undoLabel) {
        DeleteUndo(container.financeRepository, scope, snackbarHostState, undoMessage, undoLabel)
    }

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
    // Avisos "¿Anotaste hoy?": un trabajo diario por cada hora elegida.
    LaunchedEffect(appSettings?.habitRemindersEnabled, appSettings?.habitHours) {
        appSettings?.let { HabitReminderScheduler.apply(context, it.habitRemindersEnabled, it.habitHours) }
    }

    // Resumen semanal del domingo.
    LaunchedEffect(appSettings?.weeklySummaryEnabled) {
        appSettings?.let { WeeklySummaryScheduler.apply(context, it.weeklySummaryEnabled) }
    }

    // Cada vez que se abre la app: crea los cobros vencidos (y registra los automáticos).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            try {
                container.incomeRepository.reconcile(LocalDate.now())
                container.recurringRepository.reconcile(LocalDate.now())
            } catch (e: Exception) {
                // Se reintenta la próxima vez; no se escribe ningún dato en el log.
            }
        }
    }

    val onQuickAction: (QuickAction) -> Unit = { action ->
        when (action) {
            QuickAction.Income -> navController.navigate(TransactionRoute.create(type = TransactionType.INCOME))
            QuickAction.Expense -> navController.navigate(TransactionRoute.create(type = TransactionType.EXPENSE))
            QuickAction.Save -> navController.navigateTo(Destination.Savings)
            QuickAction.Pay -> navController.navigate(BillFormRoute.create())
            QuickAction.Loan -> navController.navigate(LoanFormRoute.create())
        }
    }

    // Widget "+ Gasto": abre el formulario cuando la navegación ya está lista (y una sola vez).
    LaunchedEffect(openExpense, currentRoute == null) {
        if (openExpense && currentRoute != null) {
            if (QuickLaunch.shouldOpenExpense(currentRoute)) onQuickAction(QuickAction.Expense)
            onOpenExpenseHandled()
        }
    }

    // Notificación del resumen semanal: lleva a la campanilla, donde se abre el resumen de esa semana.
    // Funciona con la app recién abierta o ya abierta en cualquier pantalla; si ya está en la campanilla,
    // la propia pantalla toma el pedido.
    LaunchedEffect(openSummaryKey, currentRoute, appSettings?.onboardingDone) {
        when (SummaryLaunch.decide(openSummaryKey, appSettings?.onboardingDone, currentRoute)) {
            SummaryLaunch.Action.OPEN_NOTIFICATIONS -> navController.navigateTo(Destination.Notifications)
            SummaryLaunch.Action.DISCARD -> onSummaryHandled()
            SummaryLaunch.Action.NONE, SummaryLaunch.Action.WAIT, SummaryLaunch.Action.SHOW_HERE -> Unit
        }
    }

    // Aviso flotante (una sola vez): "mantené apretado el + para anotar hablando".
    val voiceTipEligible = currentRoute == Destination.Home.route && appSettings?.let {
        it.onboardingDone && !it.tourPending && !it.firstExpensePending && VOICE_TIP_ID !in it.dismissedHints
    } == true
    var voiceTipVisible by remember { mutableStateOf(false) }
    val dismissVoiceTip: () -> Unit = {
        voiceTipVisible = false
        scope.launch { container.settingsRepository.dismissHint(VOICE_TIP_ID) }
    }
    LaunchedEffect(voiceTipEligible) {
        if (voiceTipEligible) {
            delay(1_500)
            voiceTipVisible = true
            delay(12_000)
            dismissVoiceTip()
        } else {
            voiceTipVisible = false
        }
    }

    CompositionLocalProvider(LocalDeleteUndo provides deleteUndo) {
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
                        onAddClick = {
                            dismissVoiceTip()
                            onQuickAction(QuickAction.Expense)
                        },
                        // Un toque: anotar a mano. Mantener apretado: anotar con la voz.
                        onAddLongClick = {
                            dismissVoiceTip()
                            navController.navigate(VoiceRoute.ROUTE) { launchSingleTop = true }
                        },
                    )
                }
            },
        ) { innerPadding ->
            MoneaNavHost(
                navController = navController,
                contentPadding = innerPadding,
                updateViewModel = updateViewModel,
                onQuickAction = onQuickAction,
                onNotificationsClick = { navController.navigateTo(Destination.Notifications) },
                openSummaryKey = openSummaryKey,
                onSummaryHandled = onSummaryHandled,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // Recorrido de 3 pasos: solo después de la configuración inicial (o si se pide desde Ayuda).
    if (appSettings?.tourPending == true) {
        FirstUseTour(onFinish = { scope.launch { container.settingsRepository.finishTour() } })
    }

    if (appSettings?.firstExpensePending == true) {
        FirstExpenseCelebration(onDismiss = { scope.launch { container.settingsRepository.dismissFirstExpense() } })
    }

    VoiceTipBubble(visible = voiceTipVisible && voiceTipEligible, onDismiss = dismissVoiceTip)
}

/** Id del aviso en `dismissedHints`; "Volver a mostrar los consejos" también lo reactiva. */
private const val VOICE_TIP_ID = "voice_plus"
