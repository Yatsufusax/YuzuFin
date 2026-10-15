package com.yuzufin.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Meta de ahorro (Fase 4). El dinero apartado sigue siendo tuyo, pero deja de estar "disponible":
 * disponible = saldo - total apartado. Apartar o retirar no es un gasto ni un ingreso.
 */
data class SavingsGoal(
    val id: Long,
    val name: String,
    val targetMinor: Long,
    /** Fecha límite opcional. */
    val deadline: LocalDate?,
    /** Porcentaje (0 a 100) de cada cobro confirmado que se aparta solo. 0 = apagado. */
    val autoPercent: Int,
    val remind: Boolean,
)

/** Aporte (positivo) o retiro (negativo) de una meta. */
data class SavingsEntry(
    val id: Long,
    val goalId: Long,
    val amountMinor: Long,
    val date: LocalDate,
    val note: String,
    /** Ingreso que originó un aporte automático; null si fue manual. */
    val sourceTransactionId: Long?,
)

enum class SavingsResult { OK, INVALID, NOT_ENOUGH_AVAILABLE, NOT_ENOUGH_SAVED }

/** Cuentas de ahorro: funciones puras con enteros. */
object SavingsMath {

    /** Dinero que se puede gastar: el saldo total menos lo apartado. Puede ser negativo. */
    fun spendable(balance: Long, totalSaved: Long): Long = balance - totalSaved

    fun remaining(targetMinor: Long, savedMinor: Long): Long = (targetMinor - savedMinor).coerceAtLeast(0L)

    fun isComplete(targetMinor: Long, savedMinor: Long): Boolean = targetMinor > 0L && savedMinor >= targetMinor

    /** Avance de 0 a 1 (tope en 1). */
    fun fraction(targetMinor: Long, savedMinor: Long): Float {
        if (targetMinor <= 0L || savedMinor <= 0L) return 0f
        return (savedMinor.toDouble() / targetMinor.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }

    /** Porcentaje entero (redondeado hacia abajo, tope 100): 99,9 % no se muestra como 100 %. */
    fun percent(targetMinor: Long, savedMinor: Long): Int {
        if (targetMinor <= 0L || savedMinor <= 0L) return 0
        return ((savedMinor * 100L) / targetMinor).coerceIn(0L, 100L).toInt()
    }

    /** Meses que quedan contando el actual (mínimo 1). null si no hay fecha límite. */
    fun monthsLeft(today: LocalDate, deadline: LocalDate?): Int? {
        deadline ?: return null
        val months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(deadline)) + 1
        return months.coerceAtLeast(1L).toInt()
    }

    /**
     * Cuánto aportar por mes para llegar a la fecha límite, redondeado hacia arriba.
     * null si no hay fecha límite o la meta ya está cumplida.
     */
    fun monthlyNeeded(targetMinor: Long, savedMinor: Long, today: LocalDate, deadline: LocalDate?): Long? {
        val left = remaining(targetMinor, savedMinor)
        if (left <= 0L) return null
        val months = monthsLeft(today, deadline) ?: return null
        return (left + months - 1) / months
    }

    /** La fecha límite ya pasó y la meta no está cumplida. */
    fun isOverdue(targetMinor: Long, savedMinor: Long, today: LocalDate, deadline: LocalDate?): Boolean =
        deadline != null && deadline.isBefore(today) && !isComplete(targetMinor, savedMinor)

    /**
     * Parte de un cobro que se aparta sola para una meta: el porcentaje del cobro, sin pasar de lo
     * que le falta a la meta ni de lo que queda del cobro tras otras metas.
     */
    fun autoContribution(incomeMinor: Long, percent: Int, goalRemaining: Long, incomeLeft: Long): Long {
        if (incomeMinor <= 0L || percent <= 0) return 0L
        val share = incomeMinor * percent.coerceAtMost(100) / 100L
        return minOf(share, goalRemaining, incomeLeft).coerceAtLeast(0L)
    }

    /** Mes como número `aaaamm` (por ejemplo 202610), para saber si ya se avisó este mes. */
    fun monthKey(date: LocalDate): Int = date.year * 100 + date.monthValue
}
