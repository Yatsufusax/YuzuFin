package com.yuzufin.app.assistant

import java.time.LocalDate

/** Lo que la persona quiere saber. */
enum class MonkiIntent {
    GREETING, THANKS, HELP,
    EXPENSES, INCOME, TOP_CATEGORIES, BALANCE, SPENDING_CHECK, COMPARE,
    BUDGETS, SAVINGS, BILLS, LOANS, TREND, PROJECTION,
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
)

/**
 * Entiende preguntas escritas de distintas maneras ("¿cuánto gasté?", "¿cuánta plata se me fue?")
 * sin depender de una frase exacta. Es una función pura y solo interpreta: no calcula ninguna cifra.
 */
object MonkiIntentParser {

    private val TOPIC_PREPOSITIONS = setOf("en", "de", "para", "por")

    /** Palabras que no sirven como tema ("en total", "de más"). */
    private val TOPIC_SKIP: Set<String> = MonkiText.STOP_WORDS + setOf(
        "total", "todo", "todos", "toda", "general", "cosas", "cosa", "esto", "eso", "mes", "semana",
        "dia", "dias", "plata", "dinero", "guita", "mango", "mangos", "categoria", "categorias",
        "cuanto", "cuanta", "cuantos", "como", "estoy", "tengo", "mas", "menos", "nada", "mucho",
        "poco", "este", "esta", "estos", "estas", "ese", "esa", "mio", "mia", "ahora", "tambien",
    )

    /** Prefijos de las palabras que dicen qué se quiere saber: no cuentan como tema. */
    private val INTENT_STEMS = listOf(
        "gast", "ingres", "cobr", "gane", "pagu", "saldo", "ahorr", "presupuest", "factura",
        "prestam", "deuda", "compar", "categori", "resumen", "meta",
    )

    fun parse(question: String, today: LocalDate, hasContext: Boolean): MonkiQuestion {
        val q = MonkiText.normalize(question)
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
            "cierro el mes", "estimacion", "estimado",
        )
        val check = spend && (
            stem("demasiad") ||
                has("mucho", "de mas", "me paso", "me estoy pasando", "excedo", "exceso", "controlado", "bien con")
            )
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

        val followUp = intent == MonkiIntent.UNKNOWN && hasContext &&
            (reading.periods.isNotEmpty() || topic != null)
        // Sin intención clara y sin nada que seguir, no hay tema que arrastrar.
        if (intent == MonkiIntent.UNKNOWN && !followUp) topic = null

        return MonkiQuestion(
            intent = intent,
            periods = reading.periods,
            ambiguousPeriod = reading.ambiguous,
            topic = topic,
            followUp = followUp,
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
