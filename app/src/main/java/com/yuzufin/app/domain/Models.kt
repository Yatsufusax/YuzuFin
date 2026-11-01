package com.yuzufin.app.domain

import java.time.LocalDate

/**
 * Tipos de movimiento ordinario. Ahorros, préstamos y demás operaciones especiales
 * tendrán su propio registro en las Fases 4 y 5, para no duplicarlas aquí.
 */
enum class TransactionType { INCOME, EXPENSE }

data class Category(
    val id: Long,
    val name: String,
    val type: TransactionType,
    /** Clave del icono (ver `categoryIcon`). */
    val iconKey: String,
    val archived: Boolean = false,
)

/** Ingreso o gasto confirmado. [amountMinor] siempre es positivo; el tipo da el signo. */
data class FinanceTransaction(
    val id: Long,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryId: Long,
    val description: String,
    val date: LocalDate,
    /**
     * Movimiento interno de un préstamo (dinero prestado, recibido o devuelto). Mueve el saldo,
     * pero no cuenta como ingreso ni gasto del mes. Se gestiona desde Obligaciones, no desde Movimientos.
     */
    val internalTransfer: Boolean = false,
)

/**
 * Punto de partida financiero: saldo que se tenía al empezar el día [startDate].
 * Los movimientos anteriores a esa fecha no se cuentan (ya están dentro del saldo).
 */
data class InitialBalance(
    val amountMinor: Long,
    val startDate: LocalDate,
)

data class AppSettings(
    val onboardingDone: Boolean,
    val currencyCode: String,
    /** Nombre que aparece en el extracto. Vacío si la persona no quiso ponerlo. */
    val userName: String = "",
    /** Recordatorios de cobro (notificaciones locales). */
    val remindersEnabled: Boolean = true,
    /** Hora aproximada del aviso diario (0 a 23). */
    val reminderHour: Int = 9,
    /** Avisos "¿Anotaste hoy?": solo llegan si ese día todavía no se anotó ningún movimiento. */
    val habitRemindersEnabled: Boolean = true,
    /** Horas (0 a 23) a las que puede llegar el aviso de hábito. */
    val habitHours: Set<Int> = setOf(13, 18, 21),
    /** Resumen de los últimos 7 días, los domingos a la noche. */
    val weeklySummaryEnabled: Boolean = true,
    /** Si el texto de la notificación incluye el importe. Apagado por defecto: se ve en la pantalla bloqueada. */
    val showAmountsInNotifications: Boolean = false,
    /** Paleta de color elegida. */
    val themePalette: ThemePalette = ThemePalette.GREEN,
    /** Claro, oscuro o el del teléfono. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Consejos del monito que la persona ya cerró con “Entendido”. */
    val dismissedHints: Set<String> = emptySet(),
    /** Tamaño del texto de la app (se suma al del teléfono). */
    val textScale: TextScale = TextScale.NORMAL,
    /** Verdadero hasta que la persona termina o saltea el recorrido del primer uso. */
    val tourPending: Boolean = false,
    /** Verdadero justo después de anotar el primer gasto, hasta que se cierra el festejo del monito. */
    val firstExpensePending: Boolean = false,
    /** El saldo de Inicio se muestra tapado (ojo cerrado); se recuerda entre aperturas. */
    val balanceHidden: Boolean = false,
)
