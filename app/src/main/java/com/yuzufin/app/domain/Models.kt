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
)

/** Ingreso o gasto confirmado. [amountMinor] siempre es positivo; el tipo da el signo. */
data class FinanceTransaction(
    val id: Long,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryId: Long,
    val description: String,
    val date: LocalDate,
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
    /** Si el texto de la notificación incluye el importe. Apagado por defecto: se ve en la pantalla bloqueada. */
    val showAmountsInNotifications: Boolean = false,
)
