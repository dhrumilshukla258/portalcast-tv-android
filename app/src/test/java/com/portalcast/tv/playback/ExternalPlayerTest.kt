package com.portalcast.tv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What another player reports when it returns (playback/ExternalPlayer.kt). */
class ExternalPlayerTest {
    @Test fun vlcReportsLongPositionAndDuration() {
        val r = ExternalPlayer.result(mapOf("extra_position" to 61_000L, "extra_duration" to 7_200_000L))
        assertEquals(61_000L, r.positionMs)
        assertEquals(7_200_000L, r.durationMs)
        assertFalse(r.completed)
    }

    @Test fun mxPlayerReportsIntsAndHowItEnded() {
        val r = ExternalPlayer.result(mapOf("position" to 5_000, "duration" to 90_000, "end_by" to "playback_completion"))
        assertEquals(5_000L, r.positionMs)
        assertEquals(90_000L, r.durationMs)
        assertTrue(r.completed)
    }

    @Test fun nothingReportedSavesNothing() {
        val r = ExternalPlayer.result(mapOf("position" to -1, "title" to "x"))
        assertNull(r.positionMs)
        assertNull(r.durationMs)
    }
}
