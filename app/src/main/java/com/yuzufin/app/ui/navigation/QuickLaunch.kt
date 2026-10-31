package com.yuzufin.app.ui.navigation

/** Pedidos de apertura rápida que llegan desde fuera de la app (por ahora, el widget "+ Gasto"). */
object QuickLaunch {
    const val EXTRA_OPEN = "open"
    const val OPEN_EXPENSE = "expense"

    /** Si todavía no terminó la configuración inicial, el widget solo abre la app normal. */
    fun shouldConsumeWithoutOpening(onboardingDone: Boolean): Boolean = !onboardingDone

    /** No se abre otro formulario si ya hay uno en pantalla (evita abrirlo dos veces). */
    fun shouldOpenExpense(currentRoute: String?): Boolean =
        currentRoute != null && !TransactionRoute.isFormRoute(currentRoute)
}
