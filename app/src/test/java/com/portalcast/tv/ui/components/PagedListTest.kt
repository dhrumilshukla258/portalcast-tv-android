package com.portalcast.tv.ui.components

import com.portalcast.tv.data.model.Paged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedListTest {

    private fun page(ids: IntRange, total: Int?) = Paged(data = ids.map { "id$it" }, totalItems = total)

    @Test
    fun appendsPagesAndAdvancesPageNumber() {
        var list = PagedList<String>()
        assertFalse(list.hasLoaded)
        list = list.append(page(1..14, 30)) { it }
        assertEquals(14, list.items.size)
        assertEquals(2, list.nextPage)
        assertFalse(list.endReached)
        list = list.append(page(15..28, 30)) { it }
        assertEquals(28, list.items.size)
        assertEquals(3, list.nextPage)
        assertFalse(list.endReached)
        list = list.append(page(29..30, 30)) { it }
        assertTrue(list.endReached)
        assertEquals(30, list.totalItems)
    }

    @Test
    fun emptyPageEndsTheList() {
        val list = PagedList<String>().append(page(1..14, null)) { it }.append(Paged(data = emptyList()), { it })
        assertTrue(list.endReached)
        assertEquals(14, list.items.size)
    }

    @Test
    fun duplicatesAreDroppedAndAPageOfOnlyDuplicatesEnds() {
        var list = PagedList<String>().append(page(1..14, 100)) { it }
        list = list.append(page(10..20, 100)) { it }
        assertEquals(20, list.items.size)
        assertEquals(list.items.toSet().size, list.items.size)
        assertFalse(list.endReached)
        list = list.append(page(1..5, 100)) { it }
        assertTrue(list.endReached)
    }

    @Test
    fun totalZeroDoesNotEndANonEmptyPage() {
        val list = PagedList<String>().append(page(1..14, 0)) { it }
        assertFalse(list.endReached)
    }

    @Test
    fun shouldLoadMoreNearTheEndOnly() {
        val list = PagedList<String>().append(page(1..14, 100)) { it }
        assertFalse(list.shouldLoadMore(lastVisibleIndex = 5))
        assertTrue(list.shouldLoadMore(lastVisibleIndex = 6))
        assertTrue(list.shouldLoadMore(lastVisibleIndex = 13))
        val ended = list.copy(endReached = true)
        assertFalse(ended.shouldLoadMore(lastVisibleIndex = 13))
        // An empty, never-loaded list asks for its first page.
        assertTrue(PagedList<String>().shouldLoadMore(-1))
    }
}
