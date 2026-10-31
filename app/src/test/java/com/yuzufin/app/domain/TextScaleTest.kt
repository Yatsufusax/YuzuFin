package com.yuzufin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextScaleTest {

    @Test
    fun normalDoesNotChangeTheSystemSize() {
        assertEquals(1f, TextScale.NORMAL.factor, 0f)
    }

    @Test
    fun sizesGrowInOrder() {
        val factors = TextScale.entries.map { it.factor }
        assertEquals(factors.sorted(), factors)
        assertTrue(TextScale.XLARGE.factor > TextScale.LARGE.factor)
        assertTrue(TextScale.LARGE.factor > TextScale.NORMAL.factor)
    }

    @Test
    fun unknownOrMissingNamesFallBackToNormal() {
        assertEquals(TextScale.NORMAL, TextScale.fromName(null))
        assertEquals(TextScale.NORMAL, TextScale.fromName(""))
        assertEquals(TextScale.NORMAL, TextScale.fromName("HUGE"))
        assertEquals(TextScale.XLARGE, TextScale.fromName("XLARGE"))
    }
}
