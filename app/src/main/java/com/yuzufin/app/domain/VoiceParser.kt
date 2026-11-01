package com.yuzufin.app.domain

import java.text.Normalizer

/**
 * Entiende frases dichas en voz alta, como "gasté veinticinco mil en el súper" o "cobré dos
 * millones". Son reglas simples (sin IA, sin red): saca el importe, si es gasto o ingreso, la
 * categoría por palabras clave y una descripción corta. Todo es una sugerencia: la persona lo
 * revisa en el formulario antes de guardar.
 */
object VoiceParser {

    data class Result(
        val type: TransactionType,
        /** Importe en unidades enteras de la moneda (sin decimales), o null si no se entendió. */
        val amount: Long?,
        val categoryId: Long?,
        val description: String,
    )

    private val INCOME_WORDS = setOf(
        "cobre", "cobro", "cobramos", "cobraron", "ingreso", "ingrese", "recibi", "recibimos", "gane",
        "ganamos", "sueldo", "salario", "deposito", "depositaron", "entro", "entraron", "pagaron",
    )

    /** Palabras que sugieren cada categoría, según la clave de su icono (no cambia aunque se renombre). */
    private val KEYWORDS: Map<String, Set<String>> = mapOf(
        "food" to setOf("comida", "super", "supermercado", "almuerzo", "cena", "desayuno", "merienda", "comi", "restaurante", "pizza", "hamburguesa", "carne", "verdura", "fruta", "panaderia", "mercado", "almacen", "bebida", "cafe", "tere", "terere", "delivery", "asado", "snack", "helado"),
        "transport" to setOf("nafta", "combustible", "colectivo", "bus", "uber", "bolt", "taxi", "pasaje", "peaje", "estacionamiento", "moto", "auto", "gasoil", "diesel", "transporte", "mecanico", "lubricentro"),
        "home" to setOf("alquiler", "hogar", "casa", "mueble", "limpieza", "reparacion", "ferreteria", "expensas", "gas"),
        "utilities" to setOf("luz", "agua", "internet", "wifi", "ande", "essap", "telefono", "celular", "plan", "tigo", "personal", "claro", "cable", "servicio", "servicios"),
        "health" to setOf("farmacia", "remedio", "medicamento", "medico", "doctor", "consulta", "dentista", "salud", "hospital", "clinica", "analisis", "seguro"),
        "education" to setOf("colegio", "escuela", "facultad", "universidad", "curso", "libro", "libros", "cuota", "matricula", "educacion", "clases"),
        "leisure" to setOf("cine", "salida", "bar", "boliche", "juego", "netflix", "spotify", "ocio", "fiesta", "viaje", "paseo", "streaming", "concierto"),
        "shopping" to setOf("compras", "ropa", "zapatillas", "zapatos", "shopping", "regalo", "celular", "electro", "tienda"),
        "salary" to setOf("sueldo", "salario", "quincena", "aguinaldo", "honorarios"),
        "extra" to setOf("extra", "venta", "vendi", "freelance", "changa", "trabajito", "comision", "propina"),
    )

    private val FILLER = setOf(
        "gaste", "pague", "compre", "pagamos", "gastamos", "gasto", "en", "de", "por", "para", "un", "una", "el", "la", "los", "las",
        "guaranies", "guarani", "gs", "pesos", "dolares", "euros", "reales", "mil", "millon", "millones", "y", "me", "con", "al", "del",
        "cobre", "cobro", "recibi", "gane", "ingreso", "ingrese", "fueron", "son", "fue", "anota", "anotar", "registra", "registrar", "agrega",
        "agregar", "sume", "sumar", "puse", "puso", "mi", "hoy", "ayer",
    )

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("(?<=\\d)\\.(?=\\d{3}(\\D|$))"), "")
            .replace(Regex("(?<=\\d),(?=\\d)"), ".")
            .replace(Regex("[^a-z0-9. ]"), " ")
            .replace(Regex("(?<![0-9])\\.|\\.(?![0-9])"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun detectType(text: String): TransactionType {
        val words = normalize(text).split(' ')
        val joined = " ${words.joinToString(" ")} "
        val income = words.any { it in INCOME_WORDS } && !joined.contains(" pague ") && !joined.contains(" gaste ")
        return if (income) TransactionType.INCOME else TransactionType.EXPENSE
    }

    fun parse(text: String, categories: List<Category>): Result {
        val type = detectType(text)
        val words = normalize(text).split(' ').filter { it.isNotEmpty() }
        val amount = extractAmount(text)
        val category = matchCategory(words, categories.filter { it.type == type })
        return Result(type, amount, category?.id, description(words, category))
    }

    private val UNITS = mapOf(
        "cero" to 0, "un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12,
        "trece" to 13, "catorce" to 14, "quince" to 15, "dieciseis" to 16, "diecisiete" to 17,
        "dieciocho" to 18, "diecinueve" to 19, "veinte" to 20, "veintiun" to 21, "veintiuno" to 21,
        "veintiuna" to 21, "veintidos" to 22, "veintitres" to 23, "veinticuatro" to 24, "veinticinco" to 25,
        "veintiseis" to 26, "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29,
        "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60, "setenta" to 70,
        "ochenta" to 80, "noventa" to 90, "cien" to 100, "ciento" to 100, "doscientos" to 200,
        "doscientas" to 200, "trescientos" to 300, "trescientas" to 300, "cuatrocientos" to 400,
        "cuatrocientas" to 400, "quinientos" to 500, "quinientas" to 500, "seiscientos" to 600,
        "seiscientas" to 600, "setecientos" to 700, "setecientas" to 700, "ochocientos" to 800,
        "ochocientas" to 800, "novecientos" to 900, "novecientas" to 900,
    )

    private fun numberOf(token: String): Double? = UNITS[token]?.toDouble() ?: token.toDoubleOrNull()

    /** El importe más grande que aparezca en la frase, en unidades enteras; null si no hay ninguno. */
    fun extractAmount(text: String): Long? {
        val tokens = normalize(text).split(' ').filter { it.isNotEmpty() }
        var best = 0.0
        var total = 0.0
        var current = 0.0
        var active = false
        var lastScale = 0.0

        fun close() {
            best = maxOf(best, total + current)
            total = 0.0; current = 0.0; active = false; lastScale = 0.0
        }

        for ((i, t) in tokens.withIndex()) {
            val n = numberOf(t)
            when {
                n != null -> { current += n; active = true }
                t == "mil" && active -> { total += maxOf(current, 1.0) * 1_000; current = 0.0; lastScale = 1_000.0 }
                t == "mil" -> { total = 1_000.0; active = true; lastScale = 1_000.0 }
                (t == "millon" || t == "millones") -> {
                    total += maxOf(current, 1.0) * 1_000_000; current = 0.0; active = true; lastScale = 1_000_000.0
                }
                t == "y" && active && tokens.getOrNull(i + 1).let { it != null && (numberOf(it) != null || it == "medio" || it == "media") } -> Unit
                (t == "medio" || t == "media") && active && lastScale > 0 -> { total += lastScale / 2; lastScale = 0.0 }
                else -> if (active) close()
            }
        }
        if (active) close()
        return if (best >= 1.0) best.toLong() else null
    }

    private fun matchCategory(words: List<String>, categories: List<Category>): Category? {
        val set = words.toSet()
        // 1) El nombre de la categoría dicho tal cual ("alimentación", "salud", "otros gastos").
        categories.firstOrNull { c ->
            val name = normalize(c.name)
            name.isNotEmpty() && (" ${words.joinToString(" ")} ").contains(" $name ")
        }?.let { return it }
        // 2) Alguna palabra de su nombre (más de 4 letras), sin plural.
        categories.firstOrNull { c ->
            normalize(c.name).split(' ').any { w -> w.length > 4 && set.any { s -> s == w || s == w.trimEnd('s') || "${s}s" == w } }
        }?.let { return it }
        // 3) Palabras clave por tipo de categoría.
        return categories.firstOrNull { c -> KEYWORDS[c.iconKey]?.any { it in set } == true }
    }

    private fun description(words: List<String>, category: Category?): String {
        val consumed = BooleanArray(words.size)
        // Marca las palabras del importe para no repetirlas en la descripción.
        words.forEachIndexed { i, w -> if (numberOf(w) != null || w in setOf("mil", "millon", "millones", "medio", "media")) consumed[i] = true }
        val afterPrep = words.indices.firstOrNull { i -> words[i] in setOf("en", "de", "por", "para") && words.take(i).any { numberOf(it) != null } }
        val candidates = (if (afterPrep != null) words.drop(afterPrep + 1) else words)
            .filterIndexed { idx, _ -> !consumed[idx + (afterPrep?.plus(1) ?: 0)] }
            .filter { it !in FILLER }
        val text = candidates.joinToString(" ").trim()
        if (text.isEmpty() || text.length > 40) return ""
        // Si solo repite el nombre de la categoría, no aporta.
        if (category != null && text == normalize(category.name)) return ""
        return text.replaceFirstChar { it.uppercase() }
    }
}
