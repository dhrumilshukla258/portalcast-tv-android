package com.portalcast.tv.ui.home

import com.portalcast.tv.ui.discover.filterSources
import com.portalcast.tv.ui.nav.HomeTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home's Movies · Series · Cast chips (owner 2026-11-13): Movies and Series are two switches over
 * Home, which shows both. From Home one narrows to it; then the other brings both back.
 */
class SectionChipsTest {
    private val home = HomeTab.DISCOVER
    private val movies = HomeTab.MOVIES
    private val series = HomeTab.SERIES
    private val cast = HomeTab.PEOPLE

    @Test fun `Home starts with Movies and Series both on`() {
        assertTrue(sectionOn(home, movies))
        assertTrue(sectionOn(home, series))
        assertFalse(sectionOn(home, cast))
    }

    @Test fun `from both, a chip narrows to its type, keeping its highlight`() {
        assertEquals(movies, nextSection(home, movies))
        assertTrue(sectionOn(movies, movies))
        assertFalse(sectionOn(movies, series))
        assertEquals(series, nextSection(home, series))
    }

    @Test fun `with one type on, the other brings both back, and so does the same one`() {
        assertEquals(home, nextSection(movies, series))
        assertEquals(home, nextSection(series, movies))
        assertEquals(home, nextSection(movies, movies))
    }

    @Test fun `Cast opens Cast, and again goes Home, from Cast a type opens alone`() {
        assertEquals(cast, nextSection(home, cast))
        assertEquals(cast, nextSection(movies, cast))
        assertEquals(home, nextSection(cast, cast))
        assertEquals(movies, nextSection(cast, movies))
        assertFalse(sectionOn(cast, movies))
    }

    @Test fun `the Filters panel lists the sources of both types on Home, of one type otherwise`() {
        val opened = mutableListOf<String>()
        val home = filterSources(null, onOpenMyLibrary = { opened += "lib:$it" }, onOpenCategories = { opened += "cat:$it" }) {}
        assertEquals(
            listOf("My library · Movies", "My library · Series", "IPTV categories · Movies", "IPTV categories · Series"),
            home.map { it.label },
        )
        home.forEach { it.onClick() }
        assertEquals(listOf("lib:movie", "lib:series", "cat:movie", "cat:series"), opened)
        assertEquals(listOf("My library", "IPTV categories"), filterSources("series", { }, { }) {}.map { it.label })
        assertEquals(listOf("IPTV categories"), filterSources("movie", null, { }) {}.map { it.label })
    }
}
