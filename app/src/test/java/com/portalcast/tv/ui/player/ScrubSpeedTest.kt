package com.portalcast.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Holding Left/Right in the player: precise at first, then a steady top speed set by the length. */
class ScrubSpeedTest {
    private val twoHours = 2 * 60 * 60 * 1000L
    private fun step(heldMs: Long, sinceLastMs: Long = 50L, durationMs: Long = twoHours) =
        PlayerViewModel.scrubStepMs(heldMs, sinceLastMs, durationMs)

    @Test fun startsAt30xAndTopsOutToCrossATwoHourFilmInAbout10Seconds() {
        assertEquals(1_500L, step(heldMs = 0))          // 50 ms of hold at 30x
        assertEquals(36_000L, step(heldMs = 3_000))     // 50 ms at 720x
        assertEquals(36_000L, step(heldMs = 60_000))    // and no faster after that
        // Holding 3 s to top speed, then 10 s more crosses the film.
        assertTrue(720f * 10_000 >= twoHours)
    }

    @Test fun shortTitlesStillMoveAtLeast120x() {
        assertEquals(6_000L, step(heldMs = 10_000, durationMs = 20 * 60 * 1000L)) // 50 ms at 120x
    }

    @Test fun speedsUpSteadilyInBetween() {
        val samples = (0..3_000 step 100).map { step(heldMs = it.toLong()) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(step(heldMs = 1_000) < step(heldMs = 3_000) / 5) // the first second stays slow
    }

    @Test fun aLateRepeatNeverJumpsFar() {
        assertEquals(180_000L, step(heldMs = 5_000, sinceLastMs = 2_000)) // capped at 250 ms
        assertEquals(0L, step(heldMs = 5_000, sinceLastMs = -5))
    }
}
