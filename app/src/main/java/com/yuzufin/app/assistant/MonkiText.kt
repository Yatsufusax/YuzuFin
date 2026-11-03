package com.yuzufin.app.assistant

import java.text.Normalizer

/** Utilidades para leer lo que escribe la persona: minúsculas, sin tildes ni signos. */
internal object MonkiText {

    private val MARKS = Regex("\\p{M}+")
    private val NOT_ALNUM = Regex("[^a-z0-9 ]")
    private val SPACES = Regex("\\s+")

    /** "¿Cuánto gasté AYER?" → "cuanto gaste ayer". */
    fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace(NOT_ALNUM, " ")
            .replace(SPACES, " ")
            .trim()

    fun words(normalized: String): List<String> =
        if (normalized.isEmpty()) emptyList() else normalized.split(' ')

    /** La frase aparece como palabras completas ("en que" sí está en "en que gaste"). */
    fun hasPhrase(normalized: String, phrase: String): Boolean = " $normalized ".contains(" $phrase ")

    /** Alguna palabra empieza con el prefijo ("gast" encuentra gasté, gastando, gastos). */
    fun hasStem(normalized: String, stems: List<String>): Boolean =
        words(normalized).any { word -> stems.any { word.startsWith(it) } }

    /** La palabra y su singular, para comparar "panes" con "pan". */
    fun variants(word: String): List<String> {
        val out = arrayListOf(word)
        if (word.length > 3 && word.endsWith("s")) out.add(word.dropLast(1))
        if (word.length > 4 && word.endsWith("es")) out.add(word.dropLast(2))
        return out
    }

    /** Palabras que no dicen de qué se habla. */
    val STOP_WORDS: Set<String> = setOf(
        "de", "del", "la", "el", "los", "las", "y", "e", "en", "para", "por", "un", "una", "uno",
        "unos", "unas", "con", "al", "a", "mi", "mis", "que", "se", "me", "lo", "le", "es", "fue",
    )
}
