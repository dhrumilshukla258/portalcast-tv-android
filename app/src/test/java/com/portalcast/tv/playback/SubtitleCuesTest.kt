package com.portalcast.tv.playback

import com.portalcast.tv.data.model.SubtitleResult
import com.portalcast.tv.data.model.downloadKey
import com.portalcast.tv.ui.player.bestMatchOrder
import com.portalcast.tv.ui.player.formatDelay
import com.portalcast.tv.ui.player.otherSubtitleSources
import com.portalcast.tv.ui.player.subtitleFailureMessage
import com.portalcast.tv.ui.player.subtitleRowLabel
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

    @Test fun `strips SSA override tags such as an8`() {
        // The old pattern compiled on the JVM but matched nothing here, and on Android it did
        // not compile at all: the app closed on the first subtitle file (owner 2026-11-04).
        val c = SubtitleCues.parse("1\n00:00:01,000 --> 00:00:02,000\n{\\an8}<b>Top</b> line {\\i1}too{\\i0}\n")
        assertEquals("Top line too", c.single().text)
    }

    @Test fun `a failed pick names OpenSubtitles and says the current subtitles stay on`() {
        assertEquals("OpenSubtitles didn't send that file. Your current subtitles stay on.", subtitleFailureMessage("os:6591885", showing = true))
        assertEquals("That subtitle didn't load. Try another one.", subtitleFailureMessage("gd:abc", showing = false))
        // Ted Lasso on 2026-11-04: OpenSubtitles' files failed while Gestdown's in the same list loaded.
        assertEquals("OpenSubtitles didn't send that file. Try one from Gestdown or SubDL.",
            subtitleFailureMessage("os:6591914", showing = false, otherSources = listOf("Gestdown", "SubDL")))
    }

    private fun result(key: String, source: String?) = SubtitleResult(downloadId = key, source = source, releaseName = key)

    @Test fun `best match skips failed files and, once OpenSubtitles failed, tries other sources first`() {
        val list = listOf(result("os:1", "opensubtitles"), result("os:2", "opensubtitles"), result("gd:a", "gestdown"), result("subdl:/b.zip", "subdl"))
        assertEquals(listOf("os:1", "os:2", "gd:a", "subdl:/b.zip"), bestMatchOrder(list, emptySet()).map { it.downloadKey })
        assertEquals(listOf("gd:a", "subdl:/b.zip", "os:2"), bestMatchOrder(list, setOf("os:1")).map { it.downloadKey })
        assertEquals(listOf("os:1", "os:2", "subdl:/b.zip"), bestMatchOrder(list, setOf("gd:a")).map { it.downloadKey })
        assertEquals(listOf("Gestdown", "SubDL"), otherSubtitleSources(list, setOf("os:1")))
        assertEquals(listOf("SubDL"), otherSubtitleSources(list, setOf("gd:a")))
    }

    @Test fun `a row shows what happened to it first, then the source`() {
        val long = "Ted.Lasso.S01E03.Trent.Crimm.The.Independent.1080p.ATVP.WEB-DL.DDP5.1.H.264-NTb"
        assertEquals("Didn't load  ·  OpenSubtitles  ·  $long  ·  EN  ·  1200↓",
            subtitleRowLabel("opensubtitles", long, "en", 1200, false, loading = false, active = false, failed = true))
        assertEquals("✓ Gestdown  ·  $long  ·  EN  ·  exact match",
            subtitleRowLabel("gestdown", long, "en", 0, true, loading = false, active = true, failed = false))
        assertEquals("Loading…  SubDL  ·  Subtitle",
            subtitleRowLabel("subdl", null, null, 0, false, loading = true, active = false, failed = true))
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
