package com.portalcast.tv.ui.discover

import com.portalcast.tv.data.model.DiscoverFacets
import com.portalcast.tv.data.model.DiscoverQuery
import com.portalcast.tv.data.model.FacetValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The IMDb rating filter (owner 2026-11-04): one more selector, carried like the others. */
class DiscoverFiltersTest {
    @Test fun `imdb rating is set, read, counted and cleared like the other selectors`() {
        val q = DiscoverQuery(type = "movie", genres = listOf("Drama")).with(FilterDim.Imdb, "7")
        assertEquals("7", q.minImdb)
        assertEquals("7", q.valueOf(FilterDim.Imdb))
        assertEquals(2, q.activeCount())
        assertNull(q.with(FilterDim.Imdb, null).minImdb)
    }

    @Test fun `imdb rating choices come from the server, absent on older servers`() {
        val facets = DiscoverFacets(imdbRatings = listOf(FacetValue("9", 285), FacetValue("8", 5644)))
        assertEquals(listOf("9", "8"), facets.optionsFor(FilterDim.Imdb).map { it.value })
        assertEquals(emptyList<FacetValue>(), DiscoverFacets().optionsFor(FilterDim.Imdb))
    }

    @Test fun `imdb rating stays when switching between Movies and Series`() {
        val q = DiscoverQuery(type = "movie", minImdb = "8")
        assertEquals("8", SharedFilter.forType(q, "series").minImdb)
    }
}
