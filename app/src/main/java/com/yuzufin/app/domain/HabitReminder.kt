package com.yuzufin.app.domain

import java.time.LocalDate

/** Momento del día de un aviso "¿Anotaste hoy?": decide qué texto se muestra. */
enum class HabitSlot { MORNING, MIDDAY, AFTERNOON, EVENING, NIGHT }

/** Reglas puras de los avisos de hábito (se prueban sin Android). */
object HabitReminder {

    const val DEFAULT_HOURS = "13,18,21"

    /** Horas que se ofrecen en Ajustes. */
    val AVAILABLE_HOURS = listOf(10, 13, 15, 18, 20, 21, 22)

    /** Hasta cuántas horas después de la hora elegida se acepta el aviso (ahorro de batería). */
    const val MAX_LATE_HOURS = 2

    /** Hoy cuenta si hay algún gasto o ingreso propio: los movimientos internos de préstamos no. */
    fun hasEntryOn(transactions: List<FinanceTransaction>, day: LocalDate): Boolean =
        transactions.any { it.date == day && !it.internalTransfer }

    /** Solo corresponde avisar si los avisos están activos y todavía no se anotó nada hoy. */
    fun shouldRemind(enabled: Boolean, hour: Int, hours: Set<Int>, hasEntryToday: Boolean): Boolean =
        enabled && hour in hours && !hasEntryToday

    /** Si Android demoró el trabajo muchas horas, ya no tiene sentido avisar. */
    fun isOnTime(nowHour: Int, slotHour: Int): Boolean {
        val late = (nowHour - slotHour + 24) % 24
        return late <= MAX_LATE_HOURS
    }

    fun slotFor(hour: Int): HabitSlot = when (hour) {
        in 5..11 -> HabitSlot.MORNING
        in 12..14 -> HabitSlot.MIDDAY
        in 15..18 -> HabitSlot.AFTERNOON
        in 19..21 -> HabitSlot.EVENING
        else -> HabitSlot.NIGHT
    }

    /** "13,18,21" -> {13, 18, 21}. Ignora lo que no sea una hora válida. */
    fun parseHours(text: String?): Set<Int> =
        text.orEmpty().split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0..23 }
            .toSortedSet()

    fun formatHours(hours: Set<Int>): String =
        hours.filter { it in 0..23 }.toSortedSet().joinToString(",")
}
