package com.yuzufin.app.ui.navigation

import androidx.navigation.NavHostController

/**
 * Pantallas de la app. Las de primer nivel viven en la barra inferior; las demás se
 * abren desde “Menú” (o desde atajos de Inicio) y tienen flecha de regreso.
 */
sealed class Destination(val route: String, val isTopLevel: Boolean) {
    data object Home : Destination("home", true)
    data object Movements : Destination("movements", true)
    data object Savings : Destination("savings", true)
    data object Menu : Destination("menu", true)
    data object Incomes : Destination("incomes", false)
    data object Obligations : Destination("obligations", false)
    data object Stats : Destination("stats", false)
    data object Settings : Destination("settings", false)
}

/** Las secciones que cuelgan de “Menú” marcan esa pestaña como activa. */
fun isMenuGroupRoute(route: String?): Boolean = when (route) {
    Destination.Menu.route,
    Destination.Incomes.route,
    Destination.Obligations.route,
    Destination.Stats.route,
    Destination.Settings.route,
    -> true
    else -> false
}

/**
 * Pestañas: vuelven a Inicio como base de la pila (el botón atrás desde cualquier pestaña
 * regresa a Inicio). Secciones secundarias: se apilan encima para volver con atrás.
 */
fun NavHostController.navigateTo(destination: Destination) {
    navigate(destination.route) {
        if (destination.isTopLevel) popUpTo(Destination.Home.route)
        launchSingleTop = true
    }
}
