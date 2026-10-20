package com.portalcast.tv.ui.components

import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.ProgressMeta
import com.portalcast.tv.data.model.ProgressRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattingTest {

    @Test
    fun clockFormatsMinutesAndHours() {
        assertEquals("00:00", Formatting.clock(0))
        assertEquals("00:00", Formatting.clock(-5000))
        assertEquals("00:05", Formatting.clock(5_999))
        assertEquals("12:34", Formatting.clock((12 * 60 + 34) * 1000L))
        assertEquals("59:59", Formatting.clock(3_599_000))
        assertEquals("1:00:00", Formatting.clock(3_600_000))
        assertEquals("1:02:03", Formatting.clock((3600 + 2 * 60 + 3) * 1000L))
    }

    @Test
    fun resumeLabel() {
        assertEquals("Resume from 01:05", Formatting.resumeFrom(65_000))
    }

    @Test
    fun episodeCodeAndMinutes() {
        assertEquals("S1E3", Formatting.episodeCode(1, 3))
        assertEquals("E3", Formatting.episodeCode(null, 3))
        assertNull(Formatting.episodeCode(2, null))
        assertEquals("1h 52m", Formatting.minutes(112))
        assertEquals("45m", Formatting.minutes(45))
        assertEquals("2h", Formatting.minutes(120))
        assertNull(Formatting.minutes(0))
    }

    @Test
    fun metaLineSkipsMissingAndPlaceholderValues() {
        val item = MediaItem(year = "2019", ratingImdb = "7.4", genresStr = "Drama", runtime = 112)
        assertEquals("2019  ·  ★ 7.4 IMDb  ·  Drama  ·  1h 52m", Formatting.metaLine(item))
        // The server's one rating wins over the portal's, and critics' scores follow it.
        val best = item.copy(ratingBest = "8.1", ratingSource = "IMDb", rtRating = "94%", metascore = "74")
        assertEquals("2019  ·  ★ 8.1 IMDb  ·  🍅 94%  ·  Metacritic 74  ·  Drama  ·  1h 52m", Formatting.metaLine(best))
        assertEquals("", Formatting.metaLine(MediaItem(year = "0", rating = "-", genresStr = "-", duration = "00:00:00")))
    }

    @Test
    fun resumableMsFollowsTheTwoSecondAndCompletedRules() {
        val rows = listOf(
            ProgressRecord(mediaId = "m1", progress = 754.5),
            ProgressRecord(mediaId = "m2", progress = 2.0),
            ProgressRecord(mediaId = "m3", progress = 500.0, completed = true),
        )
        assertEquals(754_500L, ProgressRows.resumableMs(rows, "m1"))
        assertNull(ProgressRows.resumableMs(rows, "m2"))
        assertNull(ProgressRows.resumableMs(rows, "m3"))
        assertNull(ProgressRows.resumableMs(rows, "missing"))
    }

    @Test
    fun fractionPrefersPercentThenDuration() {
        val pct = ProgressRecord(mediaId = "a", progress = 10.0, meta = ProgressMeta(progressPercent = 40, duration = 100.0).toJson())
        val dur = ProgressRecord(mediaId = "b", progress = 25.0, meta = ProgressMeta(duration = 100.0).toJson())
        val none = ProgressRecord(mediaId = "c", progress = 25.0)
        val done = ProgressRecord(mediaId = "d", progress = 25.0, completed = true)
        assertEquals(0.4f, ProgressRows.fraction(pct)!!, 0.001f)
        assertEquals(0.25f, ProgressRows.fraction(dur)!!, 0.001f)
        assertNull(ProgressRows.fraction(none))
        assertEquals(1f, ProgressRows.fraction(done)!!, 0.001f)
    }
}
