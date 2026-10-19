package com.portalcast.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoStatsTest {
    @Test fun `labels match the server`() {
        assertEquals("1080p", VideoStats.label(1920, 1080))
        assertEquals("1080p", VideoStats.label(1920, 800)) // letterboxed
        assertEquals("2160p", VideoStats.label(3840, 1600))
        assertEquals("720p", VideoStats.label(1280, 720))
        assertEquals("SD", VideoStats.label(640, 360))
    }

    @Test fun `codecs from mime or codec string`() {
        assertEquals("H.264", VideoStats.codecName("video/avc", "avc1.640028"))
        assertEquals("HEVC", VideoStats.codecName("video/hevc", null))
        assertEquals("HEVC", VideoStats.codecName(null, "hvc1.2.4.L120"))
        assertNull(VideoStats.codecName("video/x-unknown", null))
    }

    @Test fun `frame rates are trimmed`() {
        assertEquals("23.976", VideoStats.fps(23.976))
        assertEquals("25", VideoStats.fps(25.0))
        assertEquals("59.94", VideoStats.fps(59.94))
    }

    @Test fun `full description`() =
        assertEquals("1080p · 23.976 fps · H.264 · 1920×1080", VideoStats.describe(1920, 1080, 23.976, "H.264"))
}
