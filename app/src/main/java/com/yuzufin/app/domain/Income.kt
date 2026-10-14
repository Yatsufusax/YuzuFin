package com.yuzufin.app.domain

import java.time.LocalDate
import java.time.YearMonth

/** Cómo se repite un cobro. */
enum class IncomeSchedule {
    /** Un día fijo del mes (por ejemplo el 5). */
    MONTHLY,

    /** Dos días fijos del mes (quincenal: por ejemplo el 15 y el último día). */
    TWICE_MONTHLY,

    /** Fechas sueltas elegidas a mano (cobros extraordinarios o irregulares). */
    CUSTOM,
}

/**
 * Fuente de ingreso programado. [daysOfMonth] se usa en mensual y quincenal; [customDates] en
 * fechas a elección. Un día 31 en un mes de 30 días (o febrero) cae en el último día del mes.
 * [startDate] es el primer día desde el que se generan cobros, para no inventar cobros pasados.
 */
data class IncomeSource(
    val id: Long,
    val name: String,
    val amountMinor: Long,
    val categoryId: Long,
    val schedule: IncomeSchedule,
    val daysOfMonth: List<Int>,
    val customDates: List<LocalDate>,
    val startDate: LocalDate,
    /** true: hay que pulsar “Ya cobré”. false: se registra solo al llegar la fecha. */
    val requiresConfirmation: Boolean,
    val active: Boolean,
    val remind: Boolean,
)

enum class OccurrenceStatus {
    /** Llegó la fecha y falta confirmar. No toca el saldo. */
    PENDING,

    /** Confirmado (a mano o automático). Ya hay un movimiento de ingreso. */
    CONFIRMED,

    /** La persona lo descartó. No toca el saldo. */
    SKIPPED,
}

/** Un cobro concreto de una fuente. Fuente + fecha son únicas: no se puede contabilizar dos veces. */
data class IncomeOccurrence(
    val id: Long,
    val sourceId: Long,
    val dueDate: LocalDate,
    val amountMinor: Long,
    val status: OccurrenceStatus,
    val transactionId: Long?,
)

/** Reglas de fechas de los cobros programados. Funciones puras, sin reloj ni base de datos. */
object IncomeScheduler {

    /** Fechas de cobro entre [from] y [to] (ambas inclusive), ordenadas y sin repetir. */
    fun dueDates(source: IncomeSource, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        return when (source.schedule) {
            IncomeSchedule.CUSTOM ->
                source.customDates.filter { !it.isBefore(from) && !it.isAfter(to) }.distinct().sorted()

            IncomeSchedule.MONTHLY, IncomeSchedule.TWICE_MONTHLY -> {
                val result = java.util.TreeSet<LocalDate>()
                var month = YearMonth.from(from)
                val lastMonth = YearMonth.from(to)
                while (!month.isAfter(lastMonth)) {
                    for (day in source.daysOfMonth) {
                        // Un mes corto no tiene día 29, 30 o 31: se usa su último día.
                        val date = month.atDay(day.coerceIn(1, month.lengthOfMonth()))
                        if (!date.isBefore(from) && !date.isAfter(to)) result.add(date)
                    }
                    month = month.plusMonths(1)
                }
                result.toList()
            }
        }
    }

    /** El próximo cobro previsto después de [after], o null si no hay (fechas sueltas ya pasadas). */
    fun nextDueDate(source: IncomeSource, after: LocalDate): LocalDate? {
        val from = after.plusDays(1)
        val until = if (source.schedule == IncomeSchedule.CUSTOM) LocalDate.MAX else from.plusMonths(14)
        return dueDates(source, from, until).firstOrNull()
    }

    /**
     * Cobros vencidos (hasta [today]) que todavía no tienen registro en [existing].
     * [floor] es la fecha inicial de las cuentas: antes de ella no se genera nada.
     */
    fun missingOccurrences(
        source: IncomeSource,
        existing: Set<LocalDate>,
        today: LocalDate,
        floor: LocalDate,
    ): List<LocalDate> {
        if (!source.active) return emptyList()
        val from = if (source.startDate.isAfter(floor)) source.startDate else floor
        return dueDates(source, from, today).filter { it !in existing }
    }
}
