package com.yuzufin.app.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonkiVoiceTest {

    @Test fun jokesNeverContainFigures() {
        // Un chiste nunca lleva cifras: las cifras salen solo de los cálculos.
        for (region in MonkiRegion.values()) {
            for (situation in JokeSituation.values()) {
                for (seed in 0..30) {
                    val joke = MonkiVoice(region, seed).joke(situation)
                    assertTrue(joke.isNotBlank())
                    assertFalse("«$joke» lleva dígitos", joke.any { it.isDigit() })
                }
            }
        }
    }

    @Test fun everyRegionHasAllSituationsAndGreetings() {
        for (region in MonkiRegion.values()) {
            val voice = MonkiVoice(region, 3)
            assertTrue(voice.greeting().contains("Monki") || voice.greeting().isNotBlank())
            assertTrue(voice.thanks().isNotBlank())
            for (situation in JokeSituation.values()) assertTrue(voice.joke(situation).isNotBlank())
        }
    }

    @Test fun regionalFlavourIsKept() {
        val paraguay = (0..30).map { MonkiVoice(MonkiRegion.PARAGUAY, it).joke(JokeSituation.FOOD) }.toSet()
        val argentina = (0..30).map { MonkiVoice(MonkiRegion.ARGENTINA, it).joke(JokeSituation.FOOD) }.toSet()
        assertTrue(paraguay.any { it.contains("chipa") || it.contains("lomito") })
        assertTrue(argentina.any { it.contains("morfi") || it.contains("fernet") })
        assertTrue(paraguay.intersect(argentina).isEmpty())
    }
}
