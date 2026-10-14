package com.yuzufin.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {

    @Test fun lee_secciones_y_puntos() {
        val sections = ReleaseNotes.parse(
            """
            ## Nuevo
            - Extracto en PDF
            - Nombre de usuario

            ## Corregido
            * Un error al editar
            """.trimIndent(),
        )
        assertEquals(2, sections.size)
        assertEquals("Nuevo", sections[0].title)
        assertEquals(listOf("Extracto en PDF", "Nombre de usuario"), sections[0].items)
        assertEquals("Corregido", sections[1].title)
        assertEquals(listOf("Un error al editar"), sections[1].items)
    }

    @Test fun texto_sin_titulos_va_en_una_seccion_sin_nombre() {
        val sections = ReleaseNotes.parse("Mejoras varias\n- Más rápido")
        assertEquals(1, sections.size)
        assertNull(sections[0].title)
        assertEquals(listOf("Mejoras varias", "Más rápido"), sections[0].items)
    }

    @Test fun vacio_no_da_secciones() {
        assertTrue(ReleaseNotes.parse("").isEmpty())
        assertTrue(ReleaseNotes.parse("## Solo título").isEmpty())
    }
}
