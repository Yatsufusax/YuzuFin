package com.yuzufin.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {

    @Test fun lee_el_numero_de_la_etiqueta() {
        assertEquals(12L, UpdateChecker.parseBuildNumber("build-12"))
        assertEquals(7L, UpdateChecker.parseBuildNumber(" build-7 "))
    }

    @Test fun rechaza_etiquetas_ajenas() {
        assertNull(UpdateChecker.parseBuildNumber("v1.2.3"))
        assertNull(UpdateChecker.parseBuildNumber("build-"))
        assertNull(UpdateChecker.parseBuildNumber("build-12-beta"))
    }

    @Test fun lee_una_release_con_apk() {
        val json = """
            {"tag_name":"build-15","name":"YuzuFin 0.2.15","body":" Mejoras \n",
             "assets":[
               {"name":"notas.txt","browser_download_url":"https://x/notas.txt","size":10},
               {"name":"YuzuFin-0.2.15.apk","browser_download_url":"https://x/app.apk","size":4096}
             ]}
        """.trimIndent()
        val info = UpdateChecker.parseRelease(json)!!
        assertEquals(15L, info.buildNumber)
        assertEquals("YuzuFin 0.2.15", info.versionName)
        assertEquals("Mejoras", info.notes)
        assertEquals("https://x/app.apk", info.downloadUrl)
        assertEquals(4096L, info.sizeBytes)
    }

    @Test fun sin_apk_o_con_etiqueta_ajena_no_hay_actualizacion() {
        assertNull(UpdateChecker.parseRelease("""{"tag_name":"build-3","assets":[]}"""))
        assertNull(UpdateChecker.parseRelease("""{"tag_name":"v9","assets":[{"name":"a.apk","browser_download_url":"u"}]}"""))
    }
}
