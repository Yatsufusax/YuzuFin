package com.yuzufin.app.data.update

data class NotesSection(val title: String?, val items: List<String>)

/**
 * Convierte las notas de la Release (`NOVEDADES.md`) en secciones para mostrar.
 * Formato: líneas `## Título` abren una sección y las líneas `- texto` son sus puntos.
 */
object ReleaseNotes {

    /**
     * Las mismas notas como texto plano para guardarlas en la campanilla: el título de cada
     * sección y sus puntos con viñeta, separando las secciones con una línea en blanco.
     */
    fun toPlainText(raw: String): String = parse(raw).joinToString("\n\n") { section ->
        val head = section.title?.let { "$it\n" }.orEmpty()
        head + section.items.joinToString("\n") { "• $it" }
    }

    fun parse(raw: String): List<NotesSection> {
        val sections = ArrayList<NotesSection>()
        var title: String? = null
        var items = ArrayList<String>()

        fun flush() {
            if (items.isNotEmpty()) sections += NotesSection(title, items)
            items = ArrayList()
        }

        for (line in raw.lines()) {
            val text = line.trim()
            when {
                text.isEmpty() -> Unit
                text.startsWith("#") -> {
                    flush()
                    title = text.trimStart('#').trim().ifEmpty { null }
                }
                else -> items += text.trimStart('-', '*', '•').trim().ifEmpty { text }
            }
        }
        flush()
        return sections
    }
}
