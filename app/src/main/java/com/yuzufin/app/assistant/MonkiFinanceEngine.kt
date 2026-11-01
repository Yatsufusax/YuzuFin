package com.yuzufin.app.assistant

import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.FinanceTransaction
import com.yuzufin.app.domain.TransactionType
import java.text.Normalizer
import java.time.LocalDate

/**
 * Primer motor determinista de Monki AI. No es todavía un LLM: responde consultas financieras
 * acotadas usando exclusivamente datos locales que le entrega la capa de aplicación.
 * No accede a Room directamente y no puede modificar movimientos.
 */
class MonkiFinanceEngine {
    fun answer(
        question: String,
        transactions: List<FinanceTransaction>,
        categories: List<Category>,
        currency: CurrencyInfo,
        today: LocalDate = LocalDate.now(),
        locale: MonkiLocale = MonkiLocale.fromCurrency(currency.code),
    ): String {
        val q = normalize(question)
        if (q.isBlank()) return greeting(locale)

        val visible = transactions.filter { !it.internalTransfer && !it.date.isAfter(today) }
        val last7 = visible.filter { !it.date.isBefore(today.minusDays(6)) }
        val weekExpenses = last7.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor }
        val weekIncome = last7.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor }

        if (listOf("hola", "buenas", "buen dia", "buenas tardes", "buenas noches").any { q.startsWith(it) }) {
            return greeting(locale)
        }
        // Primero lo más específico (categorías y comidas), después gastos e ingresos en general.
        if (q.contains("categoria") || q.contains("en que gaste") || q.contains("donde gaste") || q.contains("mas gaste")) {
            val totals = last7.filter { it.type == TransactionType.EXPENSE }
                .groupBy { tx -> categories.firstOrNull { it.id == tx.categoryId }?.name ?: "Otros" }
                .mapValues { (_, items) -> items.sumOf { it.amountMinor } }
                .toList().sortedByDescending { it.second }
            if (totals.isEmpty()) return "Todavía no encuentro gastos registrados en los últimos 7 días. ¡Buen momento para empezar a llevar la cuenta!"
            val top = totals.take(3).joinToString("; ") { "${it.first}: ${MoneyFormatter.format(it.second, currency)}" }
            return "Tus principales categorías de gasto de los últimos 7 días son: $top. ${locale.categoryJoke}"
        }
        if (q.contains("chipa") || q.contains("delivery") || q.contains("comida")) {
            val keyword = when {
                q.contains("chipa") -> "chipa"
                q.contains("delivery") -> "delivery"
                else -> "comida"
            }
            val matching = visible.filter { it.type == TransactionType.EXPENSE &&
                (normalize(it.description).contains(keyword) ||
                    categories.firstOrNull { c -> c.id == it.categoryId }?.let { c -> normalize(c.name).contains(keyword) } == true)
            }
            val total = matching.filter { !it.date.isBefore(today.minusDays(6)) }.sumOf { it.amountMinor }
            val count = matching.count { !it.date.isBefore(today.minusDays(6)) }
            return if (count == 0) "No encuentro gastos de $keyword registrados esta semana. Si los anotás, puedo ayudarte a seguirlos."
            else "Esta semana registraste $count ${if (count == 1) "gasto" else "gastos"} relacionado(s) con $keyword, por ${MoneyFormatter.format(total, currency)}. ${locale.foodJoke}"
        }
        if (q.contains("gast")) {
            return "${locale.weekLead} En los últimos 7 días registraste ${MoneyFormatter.format(weekExpenses, currency)} en gastos. " +
                if (weekExpenses == 0L) locale.noExpenses else locale.weekJoke
        }
        if (q.contains("ingres") || q.contains("cobr")) {
            return "En los últimos 7 días registraste ${MoneyFormatter.format(weekIncome, currency)} en ingresos."
        }
        return locale.fallback
    }

    private fun greeting(locale: MonkiLocale) = locale.greeting

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}

data class MonkiLocale(
    val greeting: String,
    val weekLead: String,
    val noExpenses: String,
    val weekJoke: String,
    val categoryJoke: String,
    val foodJoke: String,
    val fallback: String,
) {
    companion object {
        fun fromCurrency(code: String) = if (code == "ARS") MonkiLocale(
            greeting = "¡Buenas! Soy Monki AI. ¿Qué querés revisar de tu plata?",
            weekLead = "Che, revisemos los números.",
            noExpenses = "¡Una semana tranqui para el bolsillo!",
            weekJoke = "Ojo, que la billetera no tiene botón de recarga infinita. 😂",
            categoryJoke = "Ya tenemos una pista de por dónde se está yendo la guita.",
            foodJoke = "La próxima chipa/hamburguesa que espere un ratito, ¿no? 😂",
            fallback = "Puedo revisar gastos de la semana, ingresos y categorías. Probá preguntarme: «¿Cuánto gasté esta semana?»",
        ) else if (code == "PYG") MonkiLocale(
            greeting = "¡Mba'éichapa! Soy Monki AI. ¿Qué revisamos de tus finanzas?",
            weekLead = "¡E'a! Veamos cómo estuvo la semana.",
            noExpenses = "¡Tu bolsillo tuvo una semana tranquila!",
            weekJoke = "Ojo, que la billetera no se llena con tereré nomás. 😂",
            categoryJoke = "Ahora ya sabemos por dónde se está escapando la plata.",
            foodJoke = "Capaz mañana le damos un descanso al lomito. 😂",
            fallback = "Puedo revisar gastos semanales, ingresos y categorías. Probá preguntarme: «¿Cuánto gasté esta semana?»",
        ) else MonkiLocale(
            greeting = "¡Hola! Soy Monki AI. ¿Qué te gustaría revisar de tus finanzas?",
            weekLead = "Revisemos los números de esta semana.",
            noExpenses = "No aparecen gastos registrados en este período.",
            weekJoke = "Un buen registro ayuda a que el dinero no desaparezca sin explicación. 🙂",
            categoryJoke = "Estas categorías muestran dónde se concentraron tus gastos.",
            foodJoke = "Registrar estos consumos ayuda a detectar patrones sin juzgarte. 🙂",
            fallback = "Puedo revisar tus gastos de la semana, ingresos y categorías. Por ejemplo: «¿Cuánto gasté esta semana?»",
        )
    }
}
