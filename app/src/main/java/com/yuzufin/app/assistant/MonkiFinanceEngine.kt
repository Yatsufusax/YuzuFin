package com.yuzufin.app.assistant

import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.BalanceCalculator
import com.yuzufin.app.domain.BillStatus
import com.yuzufin.app.domain.BudgetLevel
import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.FinanceTransaction
import com.yuzufin.app.domain.LoanDirection
import com.yuzufin.app.domain.ObligationMath
import com.yuzufin.app.domain.Recommendation
import com.yuzufin.app.domain.SavingsMath
import com.yuzufin.app.domain.StatsCalculator
import com.yuzufin.app.domain.TransactionType
import java.time.LocalDate
import java.time.YearMonth

/** Lo que responde Monki y lo que recuerda para seguir la conversación. */
data class MonkiReply(val text: String, val context: MonkiContext)

/**
 * Motor de Monki. Interpreta la pregunta ([MonkiIntentParser]) y la responde con los datos reales de
 * Monea. **Todas las cifras salen de cuentas en Kotlin** (los mismos calculadores que usan Inicio y
 * Estadísticas); el texto y los chistes solo las acompañan. Es de solo lectura: recibe una foto
 * inmutable ([MonkiSnapshot]) y no tiene acceso a ningún repositorio.
 */
class MonkiFinanceEngine {

    fun reply(
        question: String,
        data: MonkiSnapshot,
        context: MonkiContext = MonkiContext(),
        today: LocalDate = LocalDate.now(),
        seed: Int = 0,
    ): MonkiReply {
        val parsed = MonkiIntentParser.parse(question, today, hasContext = context.intent != null)
        val voice = MonkiVoice(MonkiRegion.fromCurrency(data.currency.code), seed)
        val env = Env(data, today, voice)

        var intent = parsed.intent
        if (intent == MonkiIntent.UNKNOWN) {
            val previous = context.intent
            if (parsed.followUp && previous != null) intent = previous else return MonkiReply(voice.fallback(), context)
        }

        // Saludos, ayuda y agradecimientos no cambian el hilo de la conversación.
        when (intent) {
            MonkiIntent.GREETING -> return MonkiReply(voice.greeting(), context)
            MonkiIntent.THANKS -> return MonkiReply(voice.thanks(), context)
            MonkiIntent.HELP -> return MonkiReply(voice.help(), context)
            else -> Unit
        }

        var period: MonkiPeriod? = parsed.periods.firstOrNull()
        var topic = parsed.topic
        if (topic == null && parsed.followUp && intent == context.intent) topic = context.topic

        // "¿Gasté mucho en diciembre?" con otro mes: se responde cuánto gastó en ese período.
        if (intent == MonkiIntent.SPENDING_CHECK && period != null && period.kind != MonkiPeriod.Kind.THIS_MONTH) {
            intent = MonkiIntent.EXPENSES
        }

        val needsPeriod = intent == MonkiIntent.EXPENSES || intent == MonkiIntent.INCOME ||
            intent == MonkiIntent.TOP_CATEGORIES
        if (needsPeriod && period == null) {
            if (parsed.ambiguousPeriod) return MonkiReply(voice.askLastMonth(), MonkiContext(intent, null, topic))
            period = context.period
            if (period == null) return MonkiReply(voice.askPeriod(), MonkiContext(intent, null, topic))
        }

        val text = when (intent) {
            MonkiIntent.EXPENSES -> {
                val p = period ?: return MonkiReply(voice.askPeriod(), MonkiContext(intent, null, topic))
                if (topic != null) topicAnswer(env, p, topic) else expensesAnswer(env, p)
            }
            MonkiIntent.INCOME -> incomeAnswer(env, period ?: return MonkiReply(voice.askPeriod(), MonkiContext(intent, null, topic)))
            MonkiIntent.TOP_CATEGORIES -> topCategoriesAnswer(env, period ?: return MonkiReply(voice.askPeriod(), MonkiContext(intent, null, topic)))
            MonkiIntent.BALANCE -> balanceAnswer(env)
            MonkiIntent.SPENDING_CHECK -> checkAnswer(env)
            MonkiIntent.COMPARE -> compareAnswer(env, parsed.periods)
            MonkiIntent.BUDGETS -> budgetsAnswer(env)
            MonkiIntent.SAVINGS -> savingsAnswer(env)
            MonkiIntent.BILLS -> billsAnswer(env)
            MonkiIntent.LOANS -> loansAnswer(env)
            MonkiIntent.TREND -> trendAnswer(env)
            MonkiIntent.PROJECTION -> projectionAnswer(env)
            else -> voice.fallback()
        }

        // Solo las consultas con período (y la comparación) dejan contexto para el "¿y …?".
        val remembers = intent == MonkiIntent.EXPENSES || intent == MonkiIntent.INCOME ||
            intent == MonkiIntent.TOP_CATEGORIES || intent == MonkiIntent.COMPARE
        val newContext = if (remembers) {
            MonkiContext(intent, parsed.periods.firstOrNull() ?: period ?: context.period, topic)
        } else {
            context
        }
        return MonkiReply(text, newContext)
    }

    // ----- Gastos e ingresos -------------------------------------------------------------------

    private fun expensesAnswer(env: Env, period: MonkiPeriod): String {
        val list = env.expenses(period)
        if (list.isEmpty()) {
            return "No encuentro gastos registrados ${period.phrase}." + env.joke(JokeSituation.EMPTY)
        }
        val total = list.sumOf { it.amountMinor }
        val income = env.incomes(period).sumOf { it.amountMinor }
        val top = env.byCategory(list).entries.maxByOrNull { it.value }
        val text = StringBuilder("Gastaste ${env.money(total)} ${period.phrase}, en ${movements(list.size)}.")
        if (income > 0L) text.append(" En ese período ingresaron ${env.money(income)}.")
        if (top != null && list.size > 1) {
            text.append(
                " Lo que más pesó fue ${env.categoryName(top.key)}: ${env.money(top.value)} " +
                    "(${StatsCalculator.percentOf(top.value, total)}% de tus gastos).",
            )
        }
        val situation = when {
            income > 0L && total * 2 <= income -> JokeSituation.LOW
            income > 0L && total > income -> JokeSituation.HIGH
            top != null && env.categoryOf(top.key)?.iconKey == "food" -> JokeSituation.FOOD
            else -> JokeSituation.GENERAL
        }
        return text.toString() + env.joke(situation)
    }

    private fun incomeAnswer(env: Env, period: MonkiPeriod): String {
        val list = env.incomes(period)
        if (list.isEmpty()) {
            return "No encuentro ingresos registrados ${period.phrase}." + env.joke(JokeSituation.EMPTY)
        }
        val total = list.sumOf { it.amountMinor }
        val expense = env.expenses(period).sumOf { it.amountMinor }
        val text = StringBuilder("Ingresaron ${env.money(total)} ${period.phrase}, en ${movements(list.size)}.")
        if (expense > 0L) text.append(" En ese período gastaste ${env.money(expense)}.")
        return text.toString() + env.joke(JokeSituation.GENERAL)
    }

    private fun topCategoriesAnswer(env: Env, period: MonkiPeriod): String {
        val list = env.expenses(period)
        if (list.isEmpty()) {
            return "No encuentro gastos registrados ${period.phrase}." + env.joke(JokeSituation.EMPTY)
        }
        val total = list.sumOf { it.amountMinor }
        val ranking = env.byCategory(list).entries.sortedByDescending { it.value }.take(3)
        val lines = ranking.mapIndexed { index, entry ->
            "${index + 1}. ${env.categoryName(entry.key)}: ${env.money(entry.value)} " +
                "(${StatsCalculator.percentOf(entry.value, total)}%)"
        }
        val text = "Esto es lo que más pesó en tus gastos ${period.phrase}:\n" +
            lines.joinToString("\n") + "\nEn total gastaste ${env.money(total)}."
        val situation = if (env.categoryOf(ranking.first().key)?.iconKey == "food") {
            JokeSituation.FOOD
        } else {
            JokeSituation.GENERAL
        }
        return text + env.joke(situation)
    }

    /** Gasto en una categoría o en una cosa puntual ("comida", "chipa", "nafta"). */
    private fun topicAnswer(env: Env, period: MonkiPeriod, topic: String): String {
        val forms = MonkiText.words(topic).flatMap { MonkiText.variants(it) }.toSet()
        val categories = env.data.categories.filter { category ->
            val byName = MonkiText.words(MonkiText.normalize(category.name))
                .filter { it.length >= 3 && it !in MonkiText.STOP_WORDS }
                .any { word -> MonkiText.variants(word).any { it in forms } }
            // "comida" también es la categoría de alimentación aunque se llame distinto.
            val byGenericWord = CATEGORY_WORDS[category.iconKey]?.any { it in forms } == true
            byName || byGenericWord
        }
        val categoryIds = categories.map { it.id }.toSet()

        fun matches(transaction: FinanceTransaction): Boolean =
            transaction.categoryId in categoryIds ||
                MonkiText.words(MonkiText.normalize(transaction.description))
                    .flatMap { MonkiText.variants(it) }
                    .any { it in forms }

        val everything = env.counted.filter { it.type == TransactionType.EXPENSE && matches(it) }
        if (everything.isEmpty()) {
            return "No encuentro nada de «$topic», ni como categoría ni en las descripciones de tus gastos. " +
                env.voice.vos("Preguntame en qué gastaste más y te muestro tus categorías.", "Pregúntame en qué gastaste más y te muestro tus categorías.")
        }
        val label = if (categories.size == 1) categories.first().name else topic
        val inPeriod = everything.filter { period.contains(it.date) }
        if (inPeriod.isEmpty()) return "No encuentro gastos de «$label» ${period.phrase}."
        val total = inPeriod.sumOf { it.amountMinor }
        val food = categories.any { it.iconKey == "food" } || topic in FOOD_WORDS
        return "En «$label» gastaste ${env.money(total)} ${period.phrase}, en ${movements(inPeriod.size)}." +
            env.joke(if (food) JokeSituation.FOOD else JokeSituation.GENERAL)
    }

    // ----- Saldo y análisis --------------------------------------------------------------------

    private fun balanceAnswer(env: Env): String {
        val initial = env.data.initial
            ?: return "Todavía no tengo tu saldo inicial, así que no puedo calcular cuánto tenés. " +
                "Completá la configuración inicial y vuelvo a mirar."
        val total = BalanceCalculator.availableBalance(initial, env.data.transactions)
        val saved = env.data.savingsEntries.sumOf { it.amountMinor }
        val spendable = SavingsMath.spendable(total, saved)
        val committed = ObligationMath.committed(env.data.bills)
        val free = ObligationMath.freeEstimate(spendable, committed)
        val have = env.voice.vos("tenés", "tienes")

        val text = StringBuilder()
        if (saved == 0L) {
            text.append("Tu saldo disponible es ${env.money(spendable)}.")
        } else {
            text.append("Tu saldo total es ${env.money(total)}. De eso, ${env.money(saved)} están apartados en tus metas, ")
            text.append("así que tu saldo disponible es ${env.money(spendable)}.")
        }
        if (committed > 0L) {
            text.append(" Además $have ${env.money(committed)} en facturas pendientes: el libre estimado es ${env.money(free)} (es una estimación).")
        }
        val point = StatsCalculator.monthPoint(initial, env.data.transactions, YearMonth.from(env.today))
        if (point.income > 0L || point.expense > 0L) {
            text.append(" Este mes ingresaron ${env.money(point.income)} y gastaste ${env.money(point.expense)}.")
        }
        val situation = when {
            spendable < 0L -> null
            point.income > 0L && point.expense * 2 <= point.income -> JokeSituation.LOW
            else -> JokeSituation.GENERAL
        }
        if (spendable < 0L) {
            text.append(" Está en negativo: conviene revisar en qué se fue y frenar un poco los gastos hasta que se acomode.")
        }
        return text.toString() + (situation?.let { env.joke(it) } ?: "")
    }

    private fun checkAnswer(env: Env): String {
        val month = YearMonth.from(env.today)
        val point = StatsCalculator.monthPoint(env.data.initial, env.data.transactions, month)
        if (point.income == 0L && point.expense == 0L) {
            return "Este mes todavía no hay movimientos para analizar." + env.joke(JokeSituation.EMPTY)
        }
        val alerts = StatsCalculator
            .recommendations(env.data.initial, env.data.transactions, env.data.budgets, month, env.today)
            .filter { it !is Recommendation.Scenario }
        val summary = "Este mes ingresaron ${env.money(point.income)} y gastaste ${env.money(point.expense)}."
        val rules = "Lo evalúo con reglas fijas: presupuestos, balance del mes, proyección, subida por categoría y ahorro."
        if (alerts.isEmpty()) {
            return "Mirando este mes, no veo señales de alerta. $summary $rules" + env.joke(JokeSituation.LOW)
        }
        val lines = alerts.take(3).map { "• " + describe(env, it) }
        return "Esto es lo que veo este mes:\n" + lines.joinToString("\n") + "\n$summary $rules" +
            env.joke(JokeSituation.HIGH)
    }

    private fun describe(env: Env, recommendation: Recommendation): String = when (recommendation) {
        is Recommendation.BudgetOver ->
            "Superaste el presupuesto de ${env.categoryName(recommendation.categoryId)}: gastaste " +
                "${env.money(recommendation.spentMinor)} y el tope era ${env.money(recommendation.limitMinor)}."
        is Recommendation.BudgetNear ->
            "Vas por el ${recommendation.percent}% del presupuesto de ${env.categoryName(recommendation.categoryId)}; " +
                "te quedan ${env.money(recommendation.remainingMinor)}."
        is Recommendation.Deficit ->
            "Gastaste más de lo que ingresó: ${env.money(recommendation.expenseMinor)} de gastos contra " +
                "${env.money(recommendation.incomeMinor)} de ingresos."
        is Recommendation.ProjectionAboveIncome ->
            "Si seguís a este ritmo, cerrarías el mes con unos ${env.money(recommendation.projectedMinor)} de gastos " +
                "(estimación) y por ahora ingresaron ${env.money(recommendation.incomeMinor)}."
        is Recommendation.CategoryRise ->
            "${env.categoryName(recommendation.categoryId)} subió ${recommendation.percent}% frente al mes pasado " +
                "(${env.money(recommendation.previousMinor)} → ${env.money(recommendation.currentMinor)})."
        is Recommendation.LowSavingsRate ->
            "Tu tasa de ahorro este mes es de ${recommendation.percent}%."
        is Recommendation.Scenario -> ""
    }

    private fun compareAnswer(env: Env, requested: List<MonkiPeriod>): String {
        val pair = comparisonPair(env.today, requested)
        val current = pair.first
        val baseline = pair.second
        val currentExpense = env.expenses(current).sumOf { it.amountMinor }
        val baselineExpense = env.expenses(baseline).sumOf { it.amountMinor }
        val currentIncome = env.incomes(current).sumOf { it.amountMinor }
        val baselineIncome = env.incomes(baseline).sumOf { it.amountMinor }
        if (currentExpense == 0L && baselineExpense == 0L && currentIncome == 0L && baselineIncome == 0L) {
            return "No encuentro movimientos ni ${current.phrase} ni ${baseline.phrase}, así que no hay nada para comparar." +
                env.joke(JokeSituation.EMPTY)
        }
        val lines = ArrayList<String>()
        lines.add("Gastos: ${env.money(currentExpense)} ${current.phrase} frente a ${env.money(baselineExpense)} ${baseline.phrase}" + variation(currentExpense, baselineExpense, "más", "menos"))
        lines.add("Ingresos: ${env.money(currentIncome)} ${current.phrase} frente a ${env.money(baselineIncome)} ${baseline.phrase}" + variation(currentIncome, baselineIncome, "más", "menos"))

        val changes = StatsCalculator.changes(
            env.byCategory(env.expenses(current)),
            env.byCategory(env.expenses(baseline)),
        )
        val biggest = if (currentExpense >= baselineExpense) {
            changes.filter { it.deltaMinor > 0L }.maxByOrNull { it.deltaMinor }
        } else {
            changes.filter { it.deltaMinor < 0L }.minByOrNull { it.deltaMinor }
        }
        if (biggest != null) {
            val verb = if (biggest.deltaMinor > 0L) "subió" else "bajó"
            lines.add("Lo que más $verb fue ${env.categoryName(biggest.categoryId)}: ${env.money(biggest.previousMinor)} → ${env.money(biggest.currentMinor)}.")
        }
        val situation = if (currentExpense < baselineExpense) JokeSituation.LOW else JokeSituation.GENERAL
        return lines.joinToString("\n") + env.joke(situation)
    }

    /** Qué se compara con qué. Un mes en curso se compara con los mismos días del otro mes. */
    private fun comparisonPair(today: LocalDate, requested: List<MonkiPeriod>): Pair<MonkiPeriod, MonkiPeriod> {
        if (requested.size >= 2) return requested[0] to requested[1]
        val thisMonth = MonkiPeriod(today.withDayOfMonth(1), today, MonkiPeriod.Kind.THIS_MONTH, "este mes")
        val last7 = MonkiPeriod(today.minusDays(6), today, MonkiPeriod.Kind.LAST_7_DAYS, "en los últimos 7 días")
        val lastMonthSameDays = sameDaysOfMonth(YearMonth.from(today).minusMonths(1), today, "el mes pasado (en los mismos días)")
        val picked = requested.firstOrNull() ?: return thisMonth to lastMonthSameDays
        return when (picked.kind) {
            MonkiPeriod.Kind.THIS_MONTH -> thisMonth to lastMonthSameDays
            MonkiPeriod.Kind.LAST_MONTH, MonkiPeriod.Kind.NAMED_MONTH ->
                thisMonth to sameDaysOfMonth(YearMonth.from(picked.start), today, picked.phrase + " (en los mismos días)")
            MonkiPeriod.Kind.LAST_7_DAYS ->
                picked to MonkiPeriod(today.minusDays(13), today.minusDays(7), MonkiPeriod.Kind.PREVIOUS_7_DAYS, "en los 7 días anteriores")
            MonkiPeriod.Kind.PREVIOUS_7_DAYS -> last7 to picked
            else -> picked to MonkiPeriod(
                picked.start.minusDays(picked.days),
                picked.start.minusDays(1),
                MonkiPeriod.Kind.LAST_N_DAYS,
                "en el período anterior",
            )
        }
    }

    private fun sameDaysOfMonth(month: YearMonth, today: LocalDate, phrase: String): MonkiPeriod {
        val lastDay = minOf(today.dayOfMonth, month.lengthOfMonth())
        return MonkiPeriod(month.atDay(1), month.atDay(lastDay), MonkiPeriod.Kind.NAMED_MONTH, phrase)
    }

    private fun variation(current: Long, baseline: Long, more: String, less: String): String = when {
        baseline <= 0L && current <= 0L -> "."
        baseline <= 0L -> " (no hay datos con los que comparar)."
        current == baseline -> " (igual)."
        current > baseline -> " (${(current - baseline) * 100 / baseline}% $more)."
        else -> " (${(baseline - current) * 100 / baseline}% $less)."
    }

    private fun trendAnswer(env: Env): String {
        val series = StatsCalculator.series(env.data.initial, env.data.transactions, YearMonth.from(env.today), 4)
        val completed = series.dropLast(1)
        val usable = completed.filter { it.expense > 0L }
        if (usable.size < 2) {
            return "Todavía no tengo suficientes meses completos con gastos para ver una tendencia." +
                env.joke(JokeSituation.EMPTY)
        }
        val lines = series.map { point ->
            val name = MONTH_NAMES[point.month.monthValue - 1]
            val suffix = if (point.month == YearMonth.from(env.today)) " (hasta hoy)" else ""
            "• $name: ${env.money(point.expense)}$suffix"
        }
        val last = usable.last()
        val before = usable[usable.size - 2]
        val direction = when {
            last.expense > before.expense -> "subieron ${(last.expense - before.expense) * 100 / before.expense}%"
            last.expense < before.expense -> "bajaron ${(before.expense - last.expense) * 100 / before.expense}%"
            else -> "se mantuvieron igual"
        }
        val situation = if (last.expense < before.expense) JokeSituation.LOW else JokeSituation.GENERAL
        return "Así vienen tus gastos mes a mes:\n" + lines.joinToString("\n") +
            "\nEntre ${MONTH_NAMES[before.month.monthValue - 1]} y ${MONTH_NAMES[last.month.monthValue - 1]} " +
            "tus gastos $direction." + env.joke(situation)
    }

    private fun projectionAnswer(env: Env): String {
        val month = YearMonth.from(env.today)
        val point = StatsCalculator.monthPoint(env.data.initial, env.data.transactions, month)
        val projected = StatsCalculator.projectedExpense(point.expense, env.today, month)
            ?: return "Todavía es muy pronto en el mes para estimar cómo vas a cerrar: necesito unos días más de datos."
        val text = StringBuilder(
            "Es una estimación, no un dato real: si seguís a este ritmo, cerrarías el mes con unos " +
                "${env.money(projected)} de gastos (hasta hoy llevás ${env.money(point.expense)}).",
        )
        if (point.income > 0L) text.append(" Este mes ingresaron ${env.money(point.income)}.")
        val situation = if (point.income > 0L && projected > point.income) JokeSituation.HIGH else JokeSituation.GENERAL
        return text.toString() + env.joke(situation)
    }

    // ----- Presupuestos, ahorros, facturas y préstamos -------------------------------------------

    private fun budgetsAnswer(env: Env): String {
        val spent = StatsCalculator.expenseByCategory(env.data.initial, env.data.transactions, YearMonth.from(env.today))
        val progress = StatsCalculator.budgetProgress(env.data.budgets, spent)
        if (progress.isEmpty()) {
            return env.voice.vos(
                "Todavía no tenés presupuestos cargados. Podés crearlos desde Menú → Presupuestos.",
                "Todavía no tienes presupuestos cargados. Puedes crearlos desde Menú → Presupuestos.",
            )
        }
        val lines = progress.take(5).map { item ->
            val status = when (item.level) {
                BudgetLevel.OVER -> "superado"
                BudgetLevel.WARNING -> "cerca del límite"
                BudgetLevel.OK -> "en orden"
            }
            "• ${env.categoryName(item.categoryId)}: ${env.money(item.spentMinor)} de ${env.money(item.limitMinor)} (${item.percent}%) — $status"
        }
        val over = progress.count { it.level == BudgetLevel.OVER }
        val situation = if (over == 0) JokeSituation.LOW else JokeSituation.HIGH
        return "Así van tus presupuestos de este mes:\n" + lines.joinToString("\n") + env.joke(situation)
    }

    private fun savingsAnswer(env: Env): String {
        if (env.data.goals.isEmpty()) {
            return env.voice.vos(
                "Todavía no tenés metas de ahorro. Podés crear una desde Ahorros.",
                "Todavía no tienes metas de ahorro. Puedes crear una desde Ahorros.",
            ) + env.joke(JokeSituation.EMPTY)
        }
        val totalSaved = env.data.savingsEntries.sumOf { it.amountMinor }
        val lines = env.data.goals.take(5).map { goal ->
            val saved = env.data.savingsEntries.filter { it.goalId == goal.id }.sumOf { it.amountMinor }
            val percent = SavingsMath.percent(goal.targetMinor, saved)
            val note = when {
                SavingsMath.isComplete(goal.targetMinor, saved) -> " ✅ ¡cumplida!"
                SavingsMath.isOverdue(goal.targetMinor, saved, env.today, goal.deadline) -> " (pasó la fecha límite)"
                else -> ", te faltan ${env.money(SavingsMath.remaining(goal.targetMinor, saved))}"
            }
            "• ${goal.name}: ${env.money(saved)} de ${env.money(goal.targetMinor)} ($percent%)$note"
        }
        val have = env.voice.vos("Tenés", "Tienes")
        return "$have ${env.money(totalSaved)} apartado en tus metas:\n" + lines.joinToString("\n") +
            env.joke(JokeSituation.SAVING)
    }

    private fun billsAnswer(env: Env): String {
        val pending = env.data.bills.filter { !it.paid }.sortedBy { it.dueDate }
        if (pending.isEmpty()) {
            return env.voice.vos("No tenés facturas pendientes. ¡Todo al día! ✅", "No tienes facturas pendientes. ¡Todo al día! ✅")
        }
        val lines = pending.take(5).map { bill ->
            val status = when (ObligationMath.status(bill, env.today)) {
                BillStatus.OVERDUE -> "vencida"
                BillStatus.DUE_TODAY -> "vence hoy"
                else -> {
                    val days = bill.dueDate.toEpochDay() - env.today.toEpochDay()
                    if (days == 1L) "vence mañana" else "vence en $days días"
                }
            }
            "• ${bill.name}: ${env.money(bill.amountMinor)} — $status"
        }
        val extra = if (pending.size > 5) "\n…y ${pending.size - 5} más." else ""
        return "Estas son tus facturas pendientes:\n" + lines.joinToString("\n") + extra +
            "\nEn total hay ${env.money(ObligationMath.committed(env.data.bills))} comprometido."
    }

    private fun loansAnswer(env: Env): String {
        val receivable = ObligationMath.totalOutstanding(LoanDirection.LENT, env.data.loans, env.data.loanPayments)
        val debts = ObligationMath.totalOutstanding(LoanDirection.BORROWED, env.data.loans, env.data.loanPayments)
        if (receivable == 0L && debts == 0L) {
            return env.voice.vos("No tenés préstamos pendientes, ni dados ni recibidos.", "No tienes préstamos pendientes, ni dados ni recibidos.")
        }
        val byLoan = env.data.loanPayments.groupBy { it.loanId }
        val lines = env.data.loans.mapNotNull { loan ->
            val left = ObligationMath.outstanding(loan.principalMinor, byLoan[loan.id].orEmpty())
            if (left <= 0L) {
                null
            } else if (loan.direction == LoanDirection.LENT) {
                "• ${loan.person} te debe ${env.money(left)}"
            } else {
                "• le debés ${env.money(left)} a ${loan.person}"
            }
        }
        return "Préstamos pendientes:\n" + lines.take(6).joinToString("\n") +
            "\nTe deben ${env.money(receivable)} en total y debés ${env.money(debts)}."
    }

    // ----- Utilidades --------------------------------------------------------------------------

    private fun movements(count: Int): String = if (count == 1) "1 movimiento" else "$count movimientos"

    /** Datos y atajos que comparten las respuestas. */
    private class Env(val data: MonkiSnapshot, val today: LocalDate, val voice: MonkiVoice) {
        val currency: CurrencyInfo = data.currency

        /** Ingresos y gastos que cuentan igual que en Estadísticas (sin préstamos ni fechas futuras). */
        val counted: List<FinanceTransaction> =
            data.transactions.filter { StatsCalculator.counts(data.initial, it) && !it.date.isAfter(today) }

        fun money(minor: Long): String = MoneyFormatter.format(minor, currency)

        fun expenses(period: MonkiPeriod): List<FinanceTransaction> =
            counted.filter { it.type == TransactionType.EXPENSE && period.contains(it.date) }

        fun incomes(period: MonkiPeriod): List<FinanceTransaction> =
            counted.filter { it.type == TransactionType.INCOME && period.contains(it.date) }

        fun byCategory(list: List<FinanceTransaction>): Map<Long, Long> =
            list.groupBy { it.categoryId }.mapValues { entry -> entry.value.sumOf { it.amountMinor } }

        fun categoryOf(id: Long): Category? = data.categories.firstOrNull { it.id == id }

        fun categoryName(id: Long): String = categoryOf(id)?.name ?: "Otros"

        /** Un chiste en párrafo aparte. */
        fun joke(situation: JokeSituation): String = "\n\n" + voice.joke(situation)
    }

    private companion object {
        val MONTH_NAMES = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
        )
        /** Palabras que nombran a toda una categoría (no a una cosa puntual), según la clave de su ícono. */
        val CATEGORY_WORDS: Map<String, Set<String>> = mapOf(
            "food" to setOf("comida", "comidas", "alimentos", "comer", "gastronomia"),
            "transport" to setOf("transporte", "movilidad", "pasajes"),
            "utilities" to setOf("servicios", "servicio"),
            "health" to setOf("salud", "medicina", "medicinas"),
            "education" to setOf("educacion", "estudio", "estudios"),
            "leisure" to setOf("ocio", "entretenimiento", "diversion", "salidas"),
            "shopping" to setOf("compras", "compra"),
            "home" to setOf("hogar", "casa"),
        )
        val FOOD_WORDS = setOf(
            "comida", "chipa", "delivery", "almuerzo", "cena", "desayuno", "merienda", "pizza",
            "hamburguesa", "asado", "lomito", "empanada", "empanadas", "cafe", "restaurante", "super",
        )
    }
}
