package com.portalcast.tv.ui.series

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonIdsTest {
    @Test
    fun `a merged season holds the provider season progress was saved with`() {
        assertTrue(SeasonIds.matches("m:7:574273.7~515872.1", "1"))
        assertTrue(SeasonIds.matches("m:7:574273.7~515872.1", "7"))
        assertTrue(SeasonIds.matches("5601", "5601"))
        assertFalse(SeasonIds.matches("m:7:574273.7~515872.1", "57"))
        assertFalse(SeasonIds.matches("m:7:574273.7", null))
        assertFalse(SeasonIds.matches("5601", "5602"))
    }

    @Test
    fun `its own id is found first`() {
        val seasons = listOf("m:2:9.1", "1", "m:3:8.1")
        assertEquals("1", SeasonIds.find(seasons, "1") { it })
        assertEquals("m:3:8.1", SeasonIds.find(listOf("m:2:9.4", "m:3:8.1"), "1") { it })
        assertEquals(null, SeasonIds.find(seasons, "9") { it })
    }
}
