package com.portalcast.tv.ui.search

import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchGroupingTest {
    private fun m(id: String, name: String) = MediaItem(id = id, name = name)

    @Test fun `versions collapse into one group with a clean title, order kept`() {
        val groups = SearchViewModel.group(emptyList(), listOf(m("1", "Batman Begins Hindi"), m("2", "Joker"), m("3", "Batman Begins 4K"), m("4", "Batman Begins")))
        assertEquals(listOf("Batman Begins", "Joker"), groups.map { it.title })
        assertEquals(listOf("1", "3", "4"), groups[0].variants.map { it.id })
        assertEquals("3 versions", groups[0].versionsLabel)
        assertEquals("Batman Begins", groups[0].display.displayTitle)
    }

    @Test fun `later pages extend existing groups without duplicates`() {
        val p1 = SearchViewModel.group(emptyList(), listOf(m("1", "Dune Hindi"), m("2", "Arrival")))
        val p2 = SearchViewModel.group(p1, listOf(m("3", "Dune Tamil"), m("1", "Dune Hindi"), m("5", "Sicario")))
        assertEquals(listOf("Dune", "Arrival", "Sicario"), p2.map { it.title })
        assertEquals(listOf("1", "3"), p2[0].variants.map { it.id })
    }

    @Test fun `channels match by name or exact number, capped`() {
        val chans = (1..200).map { Channel(id = "$it", name = if (it % 2 == 0) "News $it" else "Sports $it", number = it) }
        assertEquals(listOf("7"), SearchViewModel.filterChannels(chans, "7").filter { it.number == 7 }.map { it.id })
        assertEquals(SearchViewModel.MAX_CHANNELS, SearchViewModel.filterChannels(chans, "news").size)
        assertEquals(0, SearchViewModel.filterChannels(chans, "  ").size)
    }

    @Test fun `recent searches newest first, deduped case-insensitively, capped`() {
        val r = (1..6).fold(emptyList<String>()) { acc, i -> SearchViewModel.pushRecent(acc, "q$i") }
        assertEquals(listOf("Q3", "q6", "q5", "q4", "q2", "q1"), SearchViewModel.pushRecent(r, "Q3"))
    }
}
