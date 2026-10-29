package com.portalcast.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** The subtitle search text the viewer can edit (PlayerViewModel.parseSubtitleQuery). */
class SubtitleQueryTest {
    @Test fun episodeAndYearComeOutOfTheText() {
        assertEquals(PlayerViewModel.SearchQuery("Dark", null, 1, 2), PlayerViewModel.parseSubtitleQuery("Dark S01E02"))
        assertEquals(PlayerViewModel.SearchQuery("Heat", "1995", null, null), PlayerViewModel.parseSubtitleQuery("Heat (1995)"))
        assertEquals(PlayerViewModel.SearchQuery("The Office", "2005", 3, 10), PlayerViewModel.parseSubtitleQuery("The Office (2005) s3e10"))
    }

    @Test fun plainTitleStaysAsTyped() {
        assertEquals(PlayerViewModel.SearchQuery("Spirited Away", null, null, null), PlayerViewModel.parseSubtitleQuery("  Spirited   Away "))
    }
}
