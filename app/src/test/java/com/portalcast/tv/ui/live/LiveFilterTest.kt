package com.portalcast.tv.ui.live

import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.ChannelGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveFilterTest {

    private val a = Channel(id = "1", name = "A", groupId = "news")
    private val b = Channel(id = "2", name = "B", groupId = "sport")
    private val c = Channel(id = "3", name = "C", groupId = "news")
    private val all = listOf(a, b, c)

    @Test
    fun syntheticGroupsComeFirstAndPortalAllGroupsAreDropped() {
        val server = listOf(
            ChannelGroup(id = "*", title = "All"),
            ChannelGroup(id = "news", title = "News"),
            ChannelGroup(id = "fav", title = "Dupe"),
        )
        val groups = LiveFilter.withSyntheticGroups(server)
        assertEquals(listOf("recent", "fav", "*", "news"), groups.map { it.id })
        assertEquals(listOf("Recent", "Favourites", "All", "News"), groups.map { it.displayTitle })
    }

    @Test
    fun allReturnsEverything() {
        assertEquals(all, LiveFilter.filter(all, ChannelGroup.ALL_ID, emptyList(), emptyList()))
    }

    @Test
    fun serverGroupFiltersByGenreId() {
        assertEquals(listOf(a, c), LiveFilter.filter(all, "news", emptyList(), emptyList()))
        assertEquals(emptyList<Channel>(), LiveFilter.filter(all, "movies", emptyList(), emptyList()))
    }

    @Test
    fun favouritesKeepChannelListOrder() {
        assertEquals(listOf(a, c), LiveFilter.filter(all, ChannelGroup.FAVORITES_ID, listOf("3", "1", "99"), emptyList()))
    }

    @Test
    fun recentsKeepRecentOrderAndSkipUnknownIds() {
        assertEquals(listOf(c, a), LiveFilter.filter(all, ChannelGroup.RECENT_ID, emptyList(), listOf("3", "99", "1", "3")))
    }

    @Test
    fun prepareSortsByNumberAndDropsDuplicateIds() {
        val x = Channel(id = "9", name = "Zed", number = 2)
        val y = Channel(id = "8", name = "Alpha", number = 1)
        val dupe = Channel(id = "9", name = "Zed copy", number = 5)
        val unnumbered = Channel(id = "7", name = "Beta")
        assertEquals(listOf("8", "9", "7"), LiveFilter.prepare(listOf(unnumbered, x, dupe, y)).map { it.id })
        assertEquals("Zed", LiveFilter.prepare(listOf(dupe, x)).first().name)
    }

    @Test
    fun restoreGroupFallsBackToAll() {
        val groups = LiveFilter.withSyntheticGroups(listOf(ChannelGroup(id = "news", title = "News")))
        assertEquals("news", LiveFilter.restoreGroup("news", groups))
        assertEquals("fav", LiveFilter.restoreGroup("fav", groups))
        assertEquals("*", LiveFilter.restoreGroup("gone", groups))
        assertEquals("*", LiveFilter.restoreGroup(null, groups))
    }

    @Test
    fun pushRecentMovesToFrontAndCaps() {
        assertEquals(listOf("2", "1", "3"), LiveFilter.pushRecent(listOf("1", "2", "3"), "2"))
        assertEquals(listOf("9", "1"), LiveFilter.pushRecent(listOf("1", "2"), "9", max = 2))
    }
}
