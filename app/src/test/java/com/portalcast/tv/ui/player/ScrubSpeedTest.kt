package com.portalcast.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Holding Left/Right in the player: a little faster at first, then a steady speed. */
class ScrubSpeedTest {
    private fun step(heldMs: Long, sinceLastMs: Long = 50L) = PlayerViewModel.scrubStepMs(heldMs, sinceLastMs)

    @Test fun startsAt20xAndSettlesAt60x() {
        assertEquals(1_000L, step(heldMs = 0))        // 50 ms of hold at 20x
        assertEquals(3_000L, step(heldMs = 1_500))    // 50 ms at 60x
        assertEquals(3_000L, step(heldMs = 30_000))   // and no faster after that
    }

    @Test fun speedsUpSteadilyInBetween() {
        val samples = (0..1_500 step 100).map { step(heldMs = it.toLong()) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun aLateRepeatNeverJumpsFar() {
        assertEquals(15_000L, step(heldMs = 5_000, sinceLastMs = 2_000)) // capped at 250 ms
        assertEquals(0L, step(heldMs = 5_000, sinceLastMs = -5))
    }
}
