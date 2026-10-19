package com.portalcast.tv.ui.live

import com.portalcast.tv.data.model.EpgData
import com.portalcast.tv.data.model.EpgProgramme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class LiveEpgTest {

    private val utc = TimeZone.getTimeZone("UTC")

    // Seconds: 1000..2000, 2000..3000, 3000..4000, 4000..5000
    private fun p(start: Long, stop: Long, name: String? = "P$start") =
        EpgProgramme(start_timestamp = "$start", stop_timestamp = "$stop", name = name)

    private val p1 = p(1000, 2000)
    private val p2 = p(2000, 3000)
    private val p3 = p(3000, 4000)
    private val p4 = p(4000, 5000)
    private val sorted = listOf(p1, p2, p3, p4)

    @Test
    fun indexSortsByStartAndDropsEmptyChannels() {
        val epg = EpgData(data = mapOf("a" to listOf(p3, p1, p2), "b" to emptyList()))
        val idx = LiveEpg.index(epg)
        assertEquals(listOf(p1, p2, p3), idx["a"])
        assertNull(idx["b"])
    }

    @Test
    fun nowNextFindsCurrentAndFollowing() {
        assertEquals(p2 to p3, LiveEpg.nowNext(sorted, 2_500_000))
        assertEquals(p4 to null, LiveEpg.nowNext(sorted, 4_999_000))
        // Before the guide starts: nothing on, next is the first programme.
        assertEquals(null to p1, LiveEpg.nowNext(sorted, 500_000))
        assertEquals(null to null, LiveEpg.nowNext(sorted, 9_000_000))
        assertEquals(null to null, LiveEpg.nowNext(emptyList(), 0))
    }

    @Test
    fun upcomingSkipsTheCurrentProgramme() {
        assertEquals(listOf(p2, p3, p4), LiveEpg.upcoming(sorted, 1_500_000))
        assertEquals(listOf(p2, p3), LiveEpg.upcoming(sorted, 1_500_000, count = 2))
        assertEquals(listOf(p4), LiveEpg.upcoming(sorted, 3_500_000))
        assertEquals(emptyList<EpgProgramme>(), LiveEpg.upcoming(sorted, 4_500_000))
    }

    @Test
    fun upcomingWithNothingOnStartsAtTheNextProgramme() {
        assertEquals(listOf(p1, p2), LiveEpg.upcoming(sorted, 0, count = 2))
        assertEquals(emptyList<EpgProgramme>(), LiveEpg.upcoming(sorted, 9_000_000))
        assertEquals(emptyList<EpgProgramme>(), LiveEpg.upcoming(sorted, 1_500_000, count = 0))
    }

    @Test
    fun clockFormatsBothStyles() {
        // 1970-01-01 14:05 UTC
        val ms = (14 * 3600 + 5 * 60) * 1000L
        assertEquals("14:05", LiveEpg.clock(ms, use24h = true, zone = utc))
        assertEquals("2:05 PM", LiveEpg.clock(ms, use24h = false, zone = utc))
        assertEquals("00:00", LiveEpg.clock(0, use24h = true, zone = utc))
        assertEquals("12:00 AM", LiveEpg.clock(0, use24h = false, zone = utc))
        assertEquals("12:30 PM", LiveEpg.clock(12 * 3600_000L + 30 * 60_000L, use24h = false, zone = utc))
    }

    @Test
    fun timeRangeAndRowStrings() {
        val show = p(14 * 3600L, 15 * 3600L + 30 * 60, name = "  News  ")
        assertEquals("14:00 – 15:30", LiveEpg.timeRange(show, use24h = true, zone = utc))
        assertEquals("14:00", LiveEpg.timeRange(p(14 * 3600L, 0), use24h = true, zone = utc))
        assertEquals("Next 14:00 · News", LiveEpg.nextLine(show, use24h = true, zone = utc))
        assertTrue(LiveEpg.title(p(0, 1, name = " ")).isNotBlank())
    }
}
