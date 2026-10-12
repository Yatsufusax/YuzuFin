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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yuzufin.app.R
import com.yuzufin.app.ui.components.QuickActionsSheetContent
import com.yuzufin.app.ui.navigation.FloatingNavBar
import com.yuzufin.app.ui.navigation.YuzuNavHost
import com.yuzufin.app.ui.navigation.navigateTo
import kotlinx.coroutines.launch

/** Estructura principal: barra flotante, navegación, avisos y hoja de registro rápido. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showQuickSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Todavía no existen las funciones: se avisa con honestidad en qué fase llegan.
    val showComingSoon: (Int) -> Unit = { phase ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(context.getString(R.string.coming_soon_snackbar, phase))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            FloatingNavBar(
                currentRoute = currentRoute,
                onNavigate = navController::navigateTo,
                onAddClick = { showQuickSheet = true },
            )
        },
    ) { innerPadding ->
        YuzuNavHost(
            navController = navController,
            contentPadding = innerPadding,
            onQuickAction = { showComingSoon(it.phase) },
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
                    showComingSoon(action.phase)
                },
            )
        }
    }
}
