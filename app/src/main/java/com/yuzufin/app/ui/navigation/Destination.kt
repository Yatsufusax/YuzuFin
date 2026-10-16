package com.yuzufin.app.ui.navigation

import androidx.navigation.NavHostController
import com.yuzufin.app.domain.LoanDirection
import com.yuzufin.app.domain.TransactionType

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
    data object Notifications : Destination("notifications", false)
}

/** Formulario para crear o editar un movimiento. Oculta la barra inferior. */
object TransactionRoute {
    const val ARG_ID = "id"
    const val ARG_TYPE = "type"
    const val PATTERN = "transaction?$ARG_ID={$ARG_ID}&$ARG_TYPE={$ARG_TYPE}"

    /** [id] = 0 crea un movimiento nuevo; otro valor edita ese movimiento. */
    fun create(id: Long = 0L, type: TransactionType = TransactionType.EXPENSE): String =
        "transaction?$ARG_ID=$id&$ARG_TYPE=${type.name}"

    fun isFormRoute(route: String?): Boolean = route?.startsWith("transaction") == true
}

/** Formulario para crear o editar una fuente de ingreso programado. Oculta la barra inferior. */
object IncomeFormRoute {
    const val ARG_ID = "id"
    const val PATTERN = "income_form?$ARG_ID={$ARG_ID}"

    /** [id] = 0 crea una fuente nueva; otro valor edita esa fuente. */
    fun create(id: Long = 0L): String = "income_form?$ARG_ID=$id"
}

/** Formulario para crear o editar una meta de ahorro. Oculta la barra inferior. */
object SavingsGoalFormRoute {
    const val ARG_ID = "id"
    const val PATTERN = "savings_goal_form?$ARG_ID={$ARG_ID}"

    /** [id] = 0 crea una meta nueva; otro valor edita esa meta. */
    fun create(id: Long = 0L): String = "savings_goal_form?$ARG_ID=$id"
}

/** Formulario para crear o editar una factura. Oculta la barra inferior. */
object BillFormRoute {
    const val ARG_ID = "id"
    const val PATTERN = "bill_form?$ARG_ID={$ARG_ID}"

    /** [id] = 0 crea una factura nueva; otro valor edita esa factura. */
    fun create(id: Long = 0L): String = "bill_form?$ARG_ID=$id"
}

/** Formulario para crear o editar un préstamo. Oculta la barra inferior. */
object LoanFormRoute {
    const val ARG_ID = "id"
    const val ARG_DIRECTION = "direction"
    const val PATTERN = "loan_form?$ARG_ID={$ARG_ID}&$ARG_DIRECTION={$ARG_DIRECTION}"

    /** [id] = 0 crea un préstamo nuevo; [direction] es el nombre de `LoanDirection`. */
    fun create(id: Long = 0L, direction: LoanDirection = LoanDirection.LENT): String =
        "loan_form?$ARG_ID=$id&$ARG_DIRECTION=${direction.name}"
}

/** Pantallas de formulario que ocupan todo el alto: no muestran la barra inferior. */
fun isFullScreenRoute(route: String?): Boolean =
    route?.startsWith("transaction") == true ||
        route?.startsWith("income_form") == true ||
        route?.startsWith("savings_goal_form") == true ||
        route?.startsWith("bill_form") == true ||
        route?.startsWith("loan_form") == true

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
