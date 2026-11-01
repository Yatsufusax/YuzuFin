package com.yuzufin.app.ui

import com.yuzufin.app.ui.components.MascotMood
import com.yuzufin.app.ui.components.MascotPose
import com.yuzufin.app.ui.components.MascotTalk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotTalkTest {
    @Test
    fun nextMessageIsNeverTheSameAndStaysInRange() {
        for (size in 2..6) {
            for (current in 0 until size) {
                for (random in 0..60) {
                    val next = MascotTalk.nextIndex(current, size, random)
                    assertNotEquals("size=$size current=$current random=$random", current, next)
                    assertTrue(next in 0 until size)
                }
            }
        }
    }

    @Test
    fun nextMessageWithSingleOptionStaysAtZero() {
        assertEquals(0, MascotTalk.nextIndex(0, 1, 7))
        assertEquals(0, MascotTalk.nextIndex(0, 0, 7))
    }

    @Test
    fun nextMessageAcceptsNegativeRandomNumbers() {
        val next = MascotTalk.nextIndex(1, 4, -9)
        assertTrue(next in 0 until 4)
        assertNotEquals(1, next)
    }

    @Test
    fun everyMoodHasSeveralMessagesToRotate() {
        MascotMood.values().forEach { mood ->
            assertTrue("$mood", MascotTalk.lines(mood).size >= 3)
        }
    }

    @Test
    fun spentPercentIsRoundedDownAndCapped() {
        assertEquals(50, MascotTalk.spentPercent(500_000L, 1_000_000L))
        assertEquals(33, MascotTalk.spentPercent(1L, 3L))
        assertEquals(0, MascotTalk.spentPercent(100L, 0L))
        assertEquals(999, MascotTalk.spentPercent(5_000_000L, 1_000L))
    }

    @Test
    fun moodsUseTheirUsualPose() {
        assertEquals(MascotPose.MISSED_YOU, MascotMood.AWAY.pose)
        assertEquals(MascotPose.RICH, MascotMood.RICH.pose)
        assertEquals(MascotPose.OVERSPENT, MascotMood.CAREFUL.pose)
    }

    @Test
    fun richPoseHasAClip() {
        assertTrue(MascotPose.RICH.clip != null)
    }
}
