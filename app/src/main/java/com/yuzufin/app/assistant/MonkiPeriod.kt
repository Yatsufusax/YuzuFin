package com.yuzufin.app.assistant

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Un rango de fechas (ambos extremos incluidos) del que habla la persona.
 * [phrase] es cómo se dice dentro de una oración: "hoy", "este mes", "en los últimos 7 días"…
 */
data class MonkiPeriod(
    val start: LocalDate,
    val end: LocalDate,
    val kind: Kind,
    val phrase: String,
) {
    enum class Kind {
        TODAY, YESTERDAY, DAY_BEFORE, LAST_7_DAYS, PREVIOUS_7_DAYS, LAST_N_DAYS,
        THIS_MONTH, LAST_MONTH, NAMED_MONTH, THIS_YEAR, LAST_YEAR,
    }

    val days: Long get() = ChronoUnit.DAYS.between(start, end) + 1

    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

/**
 * Lo que se leyó de la frase: los períodos en el orden en que aparecen, si hay algo ambiguo
 * ("el último mes": ¿el mes pasado o los últimos 30 días?) y el texto que sobra sin esos períodos.
 */
data class MonkiPeriodReading(
    val periods: List<MonkiPeriod>,
    val ambiguous: Boolean,
    val remaining: String,
)

/**
 * Interpreta períodos escritos en lenguaje natural. Función pura: la fecha de hoy se recibe como
 * parámetro. "Esta semana" son los últimos 7 días (igual que el resumen semanal de los domingos).
 */
object MonkiPeriods {

    private val MONTH_NAMES = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    private val LAST_MONTH_AMBIGUOUS = Regex("\\bultimo mes\\b")
    private val LAST_N_DAYS = Regex("\\bultimos (\\d{1,3}) dias\\b")
    private val PREVIOUS_WEEK = Regex("\\b(semana pasada|semana anterior)\\b")
    private val THIS_WEEK = Regex("\\b(esta semana|ultima semana|semana)\\b")
    private val LAST_MONTH = Regex("\\b(mes pasado|mes anterior)\\b")
    private val NAMED_MONTH = Regex("\\b(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\b")
    private val THIS_MONTH = Regex("\\b(este mes|del mes|en el mes|mes actual|mes en curso|el mes|mes)\\b")
    private val LAST_YEAR = Regex("\\bano pasado\\b")
    private val THIS_YEAR = Regex("\\b(lo que va del ano|este ano|en el ano|del ano)\\b")
    private val DAY_BEFORE = Regex("\\b(anteayer|antes de ayer)\\b")
    private val YESTERDAY = Regex("\\bayer\\b")
    private val TODAY = Regex("\\bhoy\\b")

    fun read(normalized: String, today: LocalDate): MonkiPeriodReading {
        var work = " $normalized "
        val found = ArrayList<Pair<Int, MonkiPeriod>>()
        var ambiguous = false

        fun consume(regex: Regex, build: (MatchResult) -> MonkiPeriod?) {
            val match = regex.find(work) ?: return
            val period = build(match) ?: return
            found.add(match.range.first to period)
            work = work.replaceRange(match.range, " ".repeat(match.value.length))
        }

        // "el último mes" puede ser el mes pasado o los últimos 30 días: se pregunta.
        LAST_MONTH_AMBIGUOUS.find(work)?.let { match ->
            ambiguous = true
            work = work.replaceRange(match.range, " ".repeat(match.value.length))
        }

        consume(LAST_N_DAYS) { match ->
            val n = match.groupValues[1].toInt()
            when {
                n < 1 || n > 366 -> null
                n == 7 -> MonkiPeriod(today.minusDays(6), today, MonkiPeriod.Kind.LAST_7_DAYS, "en los últimos 7 días")
                else -> MonkiPeriod(today.minusDays(n - 1L), today, MonkiPeriod.Kind.LAST_N_DAYS, "en los últimos $n días")
            }
        }
        consume(PREVIOUS_WEEK) {
            MonkiPeriod(today.minusDays(13), today.minusDays(7), MonkiPeriod.Kind.PREVIOUS_7_DAYS, "en los 7 días anteriores")
        }
        consume(THIS_WEEK) {
            MonkiPeriod(today.minusDays(6), today, MonkiPeriod.Kind.LAST_7_DAYS, "en los últimos 7 días")
        }
        consume(LAST_MONTH) {
            val month = YearMonth.from(today).minusMonths(1)
            MonkiPeriod(month.atDay(1), month.atEndOfMonth(), MonkiPeriod.Kind.LAST_MONTH, "el mes pasado")
        }
        // Hasta dos meses por nombre ("octubre y noviembre").
        repeat(2) {
            consume(NAMED_MONTH) { match ->
                val name = match.groupValues[1].let { if (it == "setiembre") "septiembre" else it }
                val number = MONTH_NAMES.indexOf(name) + 1
                val current = today.monthValue
                when {
                    number == current ->
                        MonkiPeriod(today.withDayOfMonth(1), today, MonkiPeriod.Kind.THIS_MONTH, "este mes")
                    number < current -> {
                        val month = YearMonth.of(today.year, number)
                        MonkiPeriod(month.atDay(1), month.atEndOfMonth(), MonkiPeriod.Kind.NAMED_MONTH, "en $name")
                    }
                    else -> {
                        val month = YearMonth.of(today.year - 1, number)
                        MonkiPeriod(month.atDay(1), month.atEndOfMonth(), MonkiPeriod.Kind.NAMED_MONTH, "en $name de ${month.year}")
                    }
                }
            }
        }
        consume(THIS_MONTH) {
            MonkiPeriod(today.withDayOfMonth(1), today, MonkiPeriod.Kind.THIS_MONTH, "este mes")
        }
        consume(LAST_YEAR) {
            MonkiPeriod(
                LocalDate.of(today.year - 1, 1, 1),
                LocalDate.of(today.year - 1, 12, 31),
                MonkiPeriod.Kind.LAST_YEAR,
                "el año pasado",
            )
        }
        consume(THIS_YEAR) {
            MonkiPeriod(LocalDate.of(today.year, 1, 1), today, MonkiPeriod.Kind.THIS_YEAR, "en lo que va del año")
        }
        consume(DAY_BEFORE) {
            MonkiPeriod(today.minusDays(2), today.minusDays(2), MonkiPeriod.Kind.DAY_BEFORE, "anteayer")
        }
        consume(YESTERDAY) {
            MonkiPeriod(today.minusDays(1), today.minusDays(1), MonkiPeriod.Kind.YESTERDAY, "ayer")
        }
        consume(TODAY) {
            MonkiPeriod(today, today, MonkiPeriod.Kind.TODAY, "hoy")
        }

        val remaining = work.trim().replace(Regex("\\s+"), " ")
        return MonkiPeriodReading(found.sortedBy { it.first }.map { it.second }, ambiguous, remaining)
    }
}
