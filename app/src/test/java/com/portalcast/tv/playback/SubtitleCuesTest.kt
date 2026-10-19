package com.portalcast.tv.playback

import com.portalcast.tv.ui.player.formatDelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleCuesTest {
    private val vtt = """
        WEBVTT

        1
        00:00:01.000 --> 00:00:03.500 line:90%
        <i>Hello</i> there

        2
        00:00:05.000 --> 00:00:07.000
        Second
        line
    """.trimIndent()

    private val srt = "1\r\n00:00:01,000 --> 00:00:02,000\r\nSRT works\r\n\r\n2\r\n01:00:00,250 --> 01:00:01,000\r\nOne hour\r\n"

    @Test fun `parses vtt with settings, tags and multi-line cues`() {
        val c = SubtitleCues.parse(vtt)
        assertEquals(2, c.size)
        assertEquals(SubtitleCue(1000, 3500, "Hello there"), c[0])
        assertEquals("Second\nline", c[1].text)
    }

    @Test fun `parses srt with comma millis and hours`() {
        val c = SubtitleCues.parse(srt)
        assertEquals(listOf(1000L, 3_600_250L), c.map { it.startMs })
    }

    @Test fun `finds the cue at a position`() {
        val c = SubtitleCues.parse(vtt)
        assertNull(SubtitleCues.at(c, 500, 0))
        assertEquals("Hello there", SubtitleCues.at(c, 1000, 0)?.text)
        assertNull(SubtitleCues.at(c, 4000, 0))
        assertEquals("Second\nline", SubtitleCues.at(c, 6999, 0)?.text)
        assertNull(SubtitleCues.at(c, 7000, 0))
    }

    @Test fun `delay moves subtitles later or earlier`() {
        val c = SubtitleCues.parse(vtt)
        assertNull("+2 s: not yet at 2.5 s", SubtitleCues.at(c, 2500, 2000))
        assertEquals("Hello there", SubtitleCues.at(c, 3100, 2000)?.text)
        assertEquals("−0.5 s: already at 0.6 s", "Hello there", SubtitleCues.at(c, 600, -500)?.text)
    }

    @Test fun `overlapping cues show the one still covering the time`() {
        val c = listOf(SubtitleCue(0, 10_000, "long"), SubtitleCue(2000, 3000, "short"))
        assertEquals("short", SubtitleCues.at(c, 2500, 0)?.text)
        assertEquals("long", SubtitleCues.at(c, 5000, 0)?.text)
    }

    @Test fun `delay labels`() {
        assertEquals("In sync", formatDelay(0))
        assertEquals("Later by 0.25 s", formatDelay(250))
        assertEquals("Earlier by 1.50 s", formatDelay(-1500))
    }
}
