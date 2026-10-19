package com.portalcast.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class NextEpisodeWindowTest {
    private fun minutes(m: Int) = m * 60_000L

    @Test
    fun `sitcom shows the button about a minute before the end`() =
        assertEquals(66_000L, PlayerViewModel.nextEpisodeWindowMs(minutes(22)))

    @Test
    fun `long episodes cap at 90 seconds`() =
        assertEquals(90_000L, PlayerViewModel.nextEpisodeWindowMs(minutes(60)))

    @Test
    fun `short episodes still get 30 seconds`() =
        assertEquals(30_000L, PlayerViewModel.nextEpisodeWindowMs(minutes(5)))
}
