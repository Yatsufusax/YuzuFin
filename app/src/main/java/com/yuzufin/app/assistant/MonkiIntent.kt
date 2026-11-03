package com.yuzufin.app.assistant

import java.time.LocalDate

/** Lo que la persona quiere saber. */
enum class MonkiIntent {
    GREETING, THANKS, HELP,
    EXPENSES, INCOME, TOP_CATEGORIES, BALANCE, SPENDING_CHECK, COMPARE,
    BUDGETS, SAVINGS, BILLS, LOANS, TREND, PROJECTION,
    /** Gastos chicos y repetidos ("gasto hormiga"). */
    ANT_SPENDING,
    /** "¿Qué podría dejar de comprar para ahorrar?" (no es lo mismo que consultar las metas de ahorro). */
    SAVING_TIPS,
    /** "¿Cuánto ahorro si reduzco mis gastos un 15 %?" */
    SIMULATION,
    /** "¿En qué categoría estoy gastando demasiado?": categorías, no el balance general del mes. */
    CATEGORY_EXCESS,
    /** "¿Qué hábito financiero debería cambiar?" */
    HABITS,
    /** "¿Por qué mi plata desaparece tan rápido?" */
    EXPLAIN,
    UNKNOWN,
}

/**
 * Lo que Monki recuerda de la conversación para entender "¿y el mes pasado?" o "¿y en comida?".
 * Vive en el estado del chat y se pierde al salir de la pantalla.
 */
data class MonkiContext(
    val intent: MonkiIntent? = null,
    val period: MonkiPeriod? = null,
    val topic: String? = null,
    /** Porcentaje de la última simulación: "¿Y el mes pasado?" repite el mismo escenario. */
    val percent: Int? = null,
)

/**
 * Una pregunta ya interpretada. [followUp] es `true` cuando la frase no dice qué se quiere saber
 * ("¿y el mes pasado?") y hay que tomarlo de la conversación anterior.
 */
data class MonkiQuestion(
    val intent: MonkiIntent,
    val periods: List<MonkiPeriod>,
    val ambiguousPeriod: Boolean,
    val topic: String?,
    val followUp: Boolean,
    /** "¿Y el anterior?": el período es el que viene antes del de la pregunta previa. */
    val previousPeriod: Boolean = false,
    /** Porcentaje de una simulación ("un 15 %"), entre 1 y 100. */
    val percent: Int? = null,
    /** Había un porcentaje pero fuera de rango (0 %, 250 %…). */
    val invalidPercent: Boolean = false,
)

/**
 * Entiende preguntas escritas de distintas maneras ("¿cuánto gasté?", "¿cuánta plata se me fue?")
 * sin depender de una frase exacta. Es una función pura y solo interpreta: no calcula ninguna cifra.
 */
object MonkiIntentParser {

    private val PREVIOUS_WORDS = setOf("anterior", "anteriores", "previo", "previa")

    private val TOPIC_PREPOSITIONS = setOf("en", "de", "para", "por")

    /** Palabras que no sirven como tema ("en total", "de más"). */
    private val TOPIC_SKIP: Set<String> = MonkiText.STOP_WORDS + setOf(
        "total", "todo", "todos", "toda", "general", "cosas", "cosa", "esto", "eso", "mes", "semana",
        "dia", "dias", "plata", "dinero", "guita", "mango", "mangos", "categoria", "categorias",
        "cuanto", "cuanta", "cuantos", "como", "estoy", "tengo", "mas", "menos", "nada", "mucho",
        "poco", "este", "esta", "estos", "estas", "ese", "esa", "mio", "mia", "ahora", "tambien",
        "anterior", "anteriores", "previo", "previa", "pasado", "pasada", "hace", "comparado", "comparar",
    )

    /** Prefijos de las palabras que dicen qué se quiere saber: no cuentan como tema. */
    private val INTENT_STEMS = listOf(
        "gast", "ingres", "cobr", "gane", "pagu", "saldo", "ahorr", "presupuest", "factura",
        "prestam", "deuda", "compar", "categori", "resumen", "meta",
    )

    private val PERCENT = Regex("(\\d{1,4}(?:[.,]\\d+)?)\\s*(%|por ?ciento)")

    fun parse(question: String, today: LocalDate, hasContext: Boolean): MonkiQuestion {
        val q = MonkiText.normalize(question)
        val rawPercent = PERCENT.find(question.lowercase())?.groupValues?.get(1)
            ?.replace(',', '.')?.toDoubleOrNull()?.toInt()
        val reading = MonkiPeriods.read(q, today)

        fun has(vararg phrases: String) = phrases.any { MonkiText.hasPhrase(q, it) }
        fun stem(vararg stems: String) = MonkiText.hasStem(q, stems.toList())

        val spend = stem("gast") ||
            has("se me fue", "se me fueron", "se me va", "se me van", "salio", "salieron", "pague", "pagamos", "consumi")
        val earn = stem("ingres", "cobr", "gane", "ganan", "ganancia") ||
            has("me pagaron", "entro", "entraron", "sueldo", "salario", "recibi", "recibo")
        val compare = stem("compar") ||
            has("mas que", "menos que", "vs", "versus", "respecto", "frente a", "igual que")
        val projection = has(
            "voy a gastar", "cuanto voy a", "proyeccion", "a fin de mes", "fin de mes",
            "cierro el mes", "estimacion", "estimado", "si sigo asi", "si sigo gastando",
            "como terminare", "como termino", "como voy a terminar", "como cerrare", "como cierro",
            "a este ritmo", "con este ritmo", "terminar el mes", "terminare el mes", "cerrare el mes",
        )
        val ant = stem("hormig") || (spend && has("chicos", "chiquitos", "pequenos", "pequeno", "chiquitines", "de poco valor"))
        val simulation = rawPercent != null &&
            (stem("reduc", "recort", "baj", "disminu") || (has("si") && stem("gast", "ahorr")))
        val savingTips = has(
            "dejar de comprar", "dejar de gastar", "que puedo recortar", "que podria recortar", "que recorto",
            "en que puedo ahorrar", "donde puedo ahorrar", "como ahorro", "como puedo ahorrar", "gastar menos",
            "reducir gastos", "recortar gastos", "bajar gastos", "bajar mis gastos", "que puedo evitar",
            "que podria evitar", "que gastos puedo", "que gasto puedo", "ahorrar mas", "ahorrar mas plata",
        ) || (stem("ahorr") && has("podria", "puedo", "deberia", "comprar", "recort", "evitar"))
        val habits = stem("habit", "costumbr", "manias")
        val explain = has("desaparece", "desaparecen", "se esfuma", "se esfuman", "se evapora", "se evaporan",
            "no me alcanza", "no me rinde", "no me rinden", "no llego") ||
            (has("por que", "porque", "como es que") && has("plata", "dinero", "guita", "mango", "mangos", "sueldo", "salario"))
        val check = spend && (
            stem("demasiad") ||
                has("mucho", "de mas", "me paso", "me estoy pasando", "excedo", "exceso", "controlado", "bien con")
            )
        // "¿En qué categoría gasto demasiado?": pide *cuáles* categorías, no un chequeo general del mes.
        val excess = stem("demasiad", "excesiv") ||
            has(
                "de mas", "mucho", "me paso", "me pase", "me estoy pasando", "me excedo", "excedo", "exceso",
                "por encima", "mas de la cuenta", "mas de lo normal", "de las manos", "fuera de control",
            )
        val whichPart = stem("categori", "rubro") ||
            has("en que", "donde", "en cual", "en cuales", "cual", "cuales", "que gastos")
        val categoryExcess = (spend || stem("categori", "rubro")) && excess && whichPart
        val trend = has("tendencia", "evolucion", "mes a mes", "viene subiendo", "viene bajando", "cada vez", "ultimamente") ||
            (stem("ultimos") && has("meses"))
        val budgets = stem("presupuest") || has("tope", "topes", "limite", "limites")
        val savings = stem("ahorr") || has("meta", "metas", "alcancia", "apartado", "apartada")
        val bills = stem("factura", "vencimient", "vence") ||
            has("pagos pendientes", "que tengo que pagar", "que debo pagar", "cuentas por pagar", "por pagar")
        val loans = stem("prestam", "deuda") ||
            has("me deben", "debo", "presto", "prestaron", "me debe")
        val balance = has(
            "saldo", "cuanto tengo", "cuanta plata tengo", "cuanto dinero tengo", "como ando", "como estoy",
            "como vengo", "cuanto me queda", "me queda", "resumen", "situacion", "panorama", "disponible",
            "puedo gastar", "patrimonio", "libre", "plata tengo", "guita tengo",
        )
        val topCategories = (spend && has("en que", "donde", "mas gaste", "que mas", "principal", "principales", "mayor")) ||
            stem("categori")

        val help = has(
            "ayuda", "que podes", "que puedes", "que sabes", "que haces", "como funcionas",
            "para que servis", "para que sirves", "opciones", "comandos", "que mas podes",
        )
        val thanks = stem("gracias") || has("aguyje", "genial", "joya", "buenisimo", "excelente")
        val greeting = q.isBlank() ||
            MonkiText.words(q).firstOrNull() in setOf("hola", "holi", "ola", "buenas", "buenos", "hey", "ey") ||
            has("buen dia", "que tal", "mba eichapa", "mbaeichapa")

        val intent = when {
            ant -> MonkiIntent.ANT_SPENDING
            simulation -> MonkiIntent.SIMULATION
            savingTips -> MonkiIntent.SAVING_TIPS
            categoryExcess -> MonkiIntent.CATEGORY_EXCESS
            habits -> MonkiIntent.HABITS
            explain -> MonkiIntent.EXPLAIN
            compare -> MonkiIntent.COMPARE
            projection -> MonkiIntent.PROJECTION
            check -> MonkiIntent.SPENDING_CHECK
            trend -> MonkiIntent.TREND
            budgets -> MonkiIntent.BUDGETS
            savings -> MonkiIntent.SAVINGS
            bills -> MonkiIntent.BILLS
            loans -> MonkiIntent.LOANS
            balance -> MonkiIntent.BALANCE
            topCategories -> MonkiIntent.TOP_CATEGORIES
            spend -> MonkiIntent.EXPENSES
            earn -> MonkiIntent.INCOME
            help -> MonkiIntent.HELP
            thanks -> MonkiIntent.THANKS
            greeting -> MonkiIntent.GREETING
            else -> MonkiIntent.UNKNOWN
        }

        val remainingWords = MonkiText.words(reading.remaining)
        var topic = topicAfterPreposition(remainingWords)
        if (topic == null && intent == MonkiIntent.UNKNOWN && hasContext) {
            topic = bareTopic(remainingWords)
        }

        // "¿Y el anterior?": sin período propio, "anterior" o "previo" apuntan al período de antes.
        val previousPeriod = reading.periods.isEmpty() && hasContext &&
            remainingWords.any { it in PREVIOUS_WORDS }

        val followUp = intent == MonkiIntent.UNKNOWN && hasContext &&
            (reading.periods.isNotEmpty() || topic != null || previousPeriod)
        // Sin intención clara y sin nada que seguir, no hay tema que arrastrar.
        if (intent == MonkiIntent.UNKNOWN && !followUp) topic = null

        return MonkiQuestion(
            intent = intent,
            periods = reading.periods,
            ambiguousPeriod = reading.ambiguous,
            topic = topic,
            followUp = followUp,
            previousPeriod = previousPeriod,
            percent = rawPercent?.takeIf { simulation && it in 1..100 },
            invalidPercent = simulation && rawPercent != null && rawPercent !in 1..100,
        )
    }

    /** "cuanto gaste en el super" → "super". */
    private fun topicAfterPreposition(words: List<String>): String? {
        val index = words.indexOfFirst { it in TOPIC_PREPOSITIONS }
        if (index < 0) return null
        val topic = words.drop(index + 1).filter { word ->
            word !in TOPIC_SKIP && word.length >= 3 && INTENT_STEMS.none { word.startsWith(it) }
        }
        return topic.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    /** "y la chipa" → "chipa" (solo cuando se sigue una conversación). */
    private fun bareTopic(words: List<String>): String? {
        val topic = words.filter { word ->
            word !in TOPIC_SKIP && word.length >= 3 && INTENT_STEMS.none { word.startsWith(it) }
        }
        return topic.takeIf { it.isNotEmpty() && it.size <= 2 }?.joinToString(" ")
    }
}
