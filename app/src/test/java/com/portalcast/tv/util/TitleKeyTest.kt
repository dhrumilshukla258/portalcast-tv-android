package com.portalcast.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Mirrors the server's titleClean.ts behaviour; examples taken from its comments. */
class TitleKeyTest {
    @Test fun `language and quality versions share a key`() {
        val k = TitleKey.normalize("ABC")
        assertEquals(k, TitleKey.normalize("ABC Hindi"))
        assertEquals(k, TitleKey.normalize("ABC hin 4K"))
        assertEquals(k, TitleKey.normalize("ABC - Tamil"))
        assertEquals(k, TitleKey.normalize("ABC (ES)"))
        assertEquals(k, TitleKey.normalize("ABC South Dub"))
        assertEquals(k, TitleKey.normalize("ABC Eng Sub"))
    }

    @Test fun `mid-title colon is ignored for the key but kept for display`() {
        assertEquals(TitleKey.normalize("Murdaugh Murders Deadly Dynasty"), TitleKey.normalize("Murdaugh Murders: Deadly Dynasty"))
        assertEquals("Murdaugh Murders: Deadly Dynasty", TitleKey.stripReleaseNoise("Murdaugh Murders: Deadly Dynasty HD"))
    }

    @Test fun `channel prefixes are whole words only`() {
        assertEquals("Sunrise", TitleKey.stripReleaseNoise("Sunrise"))
        assertEquals("Starship Troopers", TitleKey.stripReleaseNoise("Starship Troopers"))
        assertEquals("Kundali Bhagya", TitleKey.stripReleaseNoise("Zee TV - Kundali Bhagya"))
    }

    @Test fun `bare Sub is not stripped, tag-only names keep a distinct key`() {
        assertEquals("Narco Sub", TitleKey.stripReleaseNoise("Narco Sub"))
        assertNotEquals(TitleKey.normalize("Maa 4K"), TitleKey.normalize("Gemini HD"))
    }

    @Test fun `variant labels`() {
        assertEquals("Hindi · 4K", TitleKey.variantLabel("ABC Hindi 4K"))
        assertEquals("Tamil", TitleKey.variantLabel("ABC - Tamil"))
        assertEquals("Hindi", TitleKey.variantLabel("ABC South Dub"))
        assertEquals("ABC", TitleKey.variantLabel("ABC"))
    }
}
