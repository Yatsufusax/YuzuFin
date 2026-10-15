package com.yuzufin.app.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObligationMathTest {

    private val today = LocalDate.of(2026, 10, 14)

    private fun bill(
        amount: Long = 100_000L,
        due: LocalDate = today.plusDays(5),
        paid: Boolean = false,
        remind: Boolean = true,
        repeatDay: Int = 0,
    ) = Bill(1, "Luz", amount, 1, due, repeatDay, remind, paid, if (paid) due else null)

    private fun loan(id: Long, direction: LoanDirection, principal: Long) =
        Loan(id, "Persona $id", direction, principal, today, "", true)

    private fun payment(loanId: Long, amount: Long) = LoanPayment(0, loanId, amount, today, "")

    @Test fun comprometido_suma_solo_las_pendientes() {
        val bills = listOf(bill(100_000L), bill(250_000L), bill(900_000L, paid = true))
        assertEquals(350_000L, ObligationMath.committed(bills))
    }

    @Test fun libre_estimado_es_disponible_menos_comprometido_y_puede_ser_negativo() {
        assertEquals(650_000L, ObligationMath.freeEstimate(1_000_000L, 350_000L))
        assertEquals(-50_000L, ObligationMath.freeEstimate(300_000L, 350_000L))
    }

    @Test fun estado_de_la_factura() {
        assertEquals(BillStatus.PAID, ObligationMath.status(bill(paid = true), today))
        assertEquals(BillStatus.OVERDUE, ObligationMath.status(bill(due = today.minusDays(1)), today))
        assertEquals(BillStatus.DUE_TODAY, ObligationMath.status(bill(due = today), today))
        assertEquals(BillStatus.UPCOMING, ObligationMath.status(bill(due = today.plusDays(1)), today))
    }

    @Test fun siguiente_vencimiento_mismo_dia_del_mes() {
        assertEquals(LocalDate.of(2026, 11, 15), ObligationMath.nextDueDate(LocalDate.of(2026, 10, 15), 15))
    }

    @Test fun siguiente_vencimiento_cruza_el_ano() {
        assertEquals(LocalDate.of(2027, 1, 10), ObligationMath.nextDueDate(LocalDate.of(2026, 12, 10), 10))
    }

    @Test fun siguiente_vencimiento_en_mes_corto_usa_el_ultimo_dia() {
        assertEquals(LocalDate.of(2027, 2, 28), ObligationMath.nextDueDate(LocalDate.of(2027, 1, 31), 31))
        assertEquals(LocalDate.of(2028, 2, 29), ObligationMath.nextDueDate(LocalDate.of(2028, 1, 31), 31))
        assertEquals(LocalDate.of(2027, 2, 28), ObligationMath.nextDueDate(LocalDate.of(2027, 1, 30), 30))
    }

    @Test fun el_dia_original_se_recupera_despues_de_un_mes_corto() {
        // Repite el 30: enero 30, febrero 28 (corto), marzo 30 otra vez.
        val feb = ObligationMath.nextDueDate(LocalDate.of(2027, 1, 30), 30)
        assertEquals(LocalDate.of(2027, 3, 30), ObligationMath.nextDueDate(feb, 30))
    }

    @Test fun aviso_de_factura_desde_el_dia_anterior() {
        assertTrue(ObligationMath.billNeedsReminder(bill(due = today.plusDays(1)), notified = false, today = today))
        assertTrue(ObligationMath.billNeedsReminder(bill(due = today), notified = false, today = today))
        assertTrue(ObligationMath.billNeedsReminder(bill(due = today.minusDays(3)), notified = false, today = today))
        assertFalse(ObligationMath.billNeedsReminder(bill(due = today.plusDays(2)), notified = false, today = today))
    }

    @Test fun no_hay_aviso_si_ya_se_aviso_esta_pagada_o_sin_recordatorio() {
        val due = today.plusDays(1)
        assertFalse(ObligationMath.billNeedsReminder(bill(due = due), notified = true, today = today))
        assertFalse(ObligationMath.billNeedsReminder(bill(due = due, paid = true), notified = false, today = today))
        assertFalse(ObligationMath.billNeedsReminder(bill(due = due, remind = false), notified = false, today = today))
    }

    @Test fun saldo_pendiente_del_prestamo() {
        assertEquals(700_000L, ObligationMath.outstanding(1_000_000L, listOf(payment(1, 200_000L), payment(1, 100_000L))))
        assertEquals(1_000_000L, ObligationMath.outstanding(1_000_000L, emptyList()))
    }

    @Test fun el_saldo_pendiente_nunca_es_negativo() {
        assertEquals(0L, ObligationMath.outstanding(1_000L, listOf(payment(1, 1_500L))))
    }

    @Test fun una_devolucion_es_valida_entre_uno_y_lo_que_falta() {
        assertTrue(ObligationMath.canRepay(1L, 500L))
        assertTrue(ObligationMath.canRepay(500L, 500L))
        assertFalse(ObligationMath.canRepay(501L, 500L))
        assertFalse(ObligationMath.canRepay(0L, 500L))
        assertFalse(ObligationMath.canRepay(-5L, 500L))
        assertFalse(ObligationMath.canRepay(1L, 0L))
    }

    @Test fun totales_por_direccion() {
        val loans = listOf(
            loan(1, LoanDirection.LENT, 1_000_000L),
            loan(2, LoanDirection.LENT, 500_000L),
            loan(3, LoanDirection.BORROWED, 800_000L),
        )
        val payments = listOf(payment(1, 400_000L), payment(3, 300_000L))
        assertEquals(1_100_000L, ObligationMath.totalOutstanding(LoanDirection.LENT, loans, payments))
        assertEquals(500_000L, ObligationMath.totalOutstanding(LoanDirection.BORROWED, loans, payments))
    }

    @Test fun patrimonio_con_ahorro_y_prestamos() {
        // Disponible 700.000 + ahorrado 300.000 + te deben 1.100.000 - debés 500.000.
        assertEquals(1_600_000L, BalanceCalculator.netWorth(700_000L, 300_000L, 1_100_000L, 500_000L))
    }

    @Test fun ventana_de_fin_de_mes() {
        assertTrue(ObligationMath.isMonthEndWindow(LocalDate.of(2026, 10, 31)))
        assertTrue(ObligationMath.isMonthEndWindow(LocalDate.of(2026, 10, 30)))
        assertFalse(ObligationMath.isMonthEndWindow(LocalDate.of(2026, 10, 29)))
        assertTrue(ObligationMath.isMonthEndWindow(LocalDate.of(2027, 2, 27)))
        assertFalse(ObligationMath.isMonthEndWindow(LocalDate.of(2027, 2, 26)))
        assertTrue(ObligationMath.isMonthEndWindow(LocalDate.of(2028, 2, 28)))
        assertFalse(ObligationMath.isMonthEndWindow(LocalDate.of(2028, 2, 27)))
    }

    @Test fun un_prestamo_mueve_el_saldo_pero_no_los_ingresos_ni_gastos_del_mes() {
        val initial = InitialBalance(amountMinor = 1_000_000L, startDate = LocalDate.of(2026, 1, 1))
        val transactions = listOf(
            FinanceTransaction(1, TransactionType.INCOME, 500_000L, 1, "Sueldo", today),
            FinanceTransaction(2, TransactionType.EXPENSE, 200_000L, 2, "Super", today),
            // Presté 300.000: sale del saldo, pero no es un gasto.
            FinanceTransaction(3, TransactionType.EXPENSE, 300_000L, 3, "Préstamo a Ana", today, internalTransfer = true),
            // Ana devolvió 100.000: entra al saldo, pero no es un ingreso.
            FinanceTransaction(4, TransactionType.INCOME, 100_000L, 3, "Devolución de Ana", today, internalTransfer = true),
        )
        assertEquals(1_100_000L, BalanceCalculator.availableBalance(initial, transactions))
        val month = BalanceCalculator.monthTotals(initial, transactions, YearMonth.from(today))
        assertEquals(500_000L, month.income)
        assertEquals(200_000L, month.expense)
    }
}
