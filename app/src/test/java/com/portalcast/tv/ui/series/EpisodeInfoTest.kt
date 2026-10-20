package com.portalcast.tv.ui.series

import com.portalcast.tv.data.model.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** Shapes taken from the owner's Stalker portal (Kim's Convenience) plus common Xtream forms. */
class EpisodeInfoTest {
    private fun ep(name: String, num: Int? = null) = MediaItem(id = name, name = name, episodeNum = num)

    @Test fun `number and title come from the name, not the list position`() {
        val e = ep("Episode 13. Friends and Family")
        assertEquals(13, EpisodeInfo.number(e, 0))
        assertEquals("Friends and Family", EpisodeInfo.title(e, 0))
        assertEquals(10, EpisodeInfo.number(ep("Episode 10.  Who's Pranking Who?"), 3))
        assertEquals("Who's Pranking Who?", EpisodeInfo.title(ep("Episode 10.  Who's Pranking Who?"), 3))
    }

    @Test fun `other common episode name forms`() {
        assertEquals(4, EpisodeInfo.number(ep("S01E04 The Pilot"), 0))
        assertEquals("The Pilot", EpisodeInfo.title(ep("S01E04 The Pilot"), 0))
        assertEquals(7, EpisodeInfo.number(ep("Ep 7 - Night"), 0))
        assertEquals("Night", EpisodeInfo.title(ep("Ep 7 - Night"), 0))
        assertEquals(3, EpisodeInfo.number(ep("3. Third"), 0))
        assertEquals(5, EpisodeInfo.number(ep("Some Title", num = 5), 0))
        assertEquals(9, EpisodeInfo.number(ep("Some Title"), 8))
        assertEquals("Episode 2", EpisodeInfo.title(ep("Episode 2"), 0))
    }

    @Test fun `newest-first portal order becomes ascending`() {
        val portal = listOf(13, 12, 11, 10, 9, 7, 6, 5).map { ep("Episode $it. Title $it") }
        assertEquals(listOf(5, 6, 7, 9, 10, 11, 12, 13), EpisodeInfo.sortEpisodes(portal).map { EpisodeInfo.number(it, 0) })
    }

    @Test fun `seasons sorted and labelled`() {
        val seasons = (5 downTo 1).map { MediaItem(id = "s$it", name = "Season $it. Kim's Convenience : Season 0$it") }
        val sorted = EpisodeInfo.sortSeasons(seasons)
        assertEquals(listOf("s1", "s2", "s3", "s4", "s5"), sorted.map { it.id })
        assertEquals("Season 5", EpisodeInfo.seasonLabel(seasons[0], 0))
    }
    @Test
    fun `air dates move out of the title`() {
        val kamali = MediaItem(id = "1", name = "2026-05-26 - Kamini Secretly Plots")
        assertEquals("Kamini Secretly Plots", EpisodeInfo.title(kamali, 0, "Kamali"))
        assertEquals("26 May 2026", EpisodeInfo.airDate(kamali))

        val showName = MediaItem(id = "2", name = "2026-05-30 - Kamali")
        assertEquals("Episode 3", EpisodeInfo.title(showName, 2, "Kamali"))

        val pushpa = MediaItem(id = "3", name = "Episode 1294. 25-07-2026 : Ep. 1294 - A Tough Time For Pushpa")
        assertEquals("A Tough Time For Pushpa", EpisodeInfo.title(pushpa, 0))
        assertEquals("25 Jul 2026", EpisodeInfo.airDate(pushpa))
        assertEquals(1294, EpisodeInfo.number(pushpa, 0))

        assertEquals(null, EpisodeInfo.airDate(MediaItem(id = "4", name = "Episode 6. The Kim Cup")))
    }
}
