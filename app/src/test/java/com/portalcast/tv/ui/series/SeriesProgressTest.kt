package com.portalcast.tv.ui.series

import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.ProgressMeta
import com.portalcast.tv.data.model.ProgressRecord
import com.portalcast.tv.ui.movies.mergedWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesProgressTest {

    private val meta = ProgressMeta(
        id = "ep3", itemId = "ep3", playbackFileId = "ep3", mediaId = "s1", seasonId = "season1",
        type = "series", title = "Show", episodeTitle = "The Third", duration = 1000.0, progressPercent = 30, series_number = 1,
    )
    private val episodeRow = ProgressRecord(mediaId = "ep3", progress = 300.0, meta = meta.toJson())
    private val seriesRow = ProgressRecord(mediaId = "s1", progress = 300.0, meta = meta.toJson())
    private val episodes = listOf(
        MediaItem(id = "ep1", name = "The First", episodeNum = 1),
        MediaItem(id = "ep3", name = "The Third", episodeNum = 3, seriesNumber = 1),
    )

    @Test
    fun episodeFractionsKeyByEpisodeIdNotSeriesId() {
        val f = SeriesProgress.episodeFractions(listOf(episodeRow, seriesRow), "s1")
        assertEquals(0.3f, f["ep3"]!!, 0.001f)
        assertNull(f["s1"])
        // Only the series row present (e.g. older save): still maps via meta.id.
        assertEquals(0.3f, SeriesProgress.episodeFractions(listOf(seriesRow), "s1")["ep3"]!!, 0.001f)
    }

    @Test
    fun continueRowIsTheUnfinishedSeriesRow() {
        assertEquals(seriesRow, SeriesProgress.continueRow(listOf(episodeRow, seriesRow), "s1"))
        assertNull(SeriesProgress.continueRow(listOf(seriesRow.copy(completed = true)), "s1"))
        assertNull(SeriesProgress.continueRow(listOf(episodeRow), "s1"))
    }

    @Test
    fun findEpisodeByIdThenTitle() {
        assertEquals("ep3", SeriesProgress.findEpisode(meta, episodes)?.id)
        val byTitle = meta.copy(id = "file99", itemId = "file99", playbackFileId = "file99", episodeTitle = "the first")
        assertEquals("ep1", SeriesProgress.findEpisode(byTitle, episodes)?.id)
        assertNull(SeriesProgress.findEpisode(meta.copy(id = "x", itemId = null, playbackFileId = null, episodeTitle = null), episodes))
    }

    @Test
    fun resumeLabelUsesEpisodeCodeWhenKnown() {
        assertEquals("Resume S1E3 · 05:00", SeriesProgress.resumeLabel(meta, episodes[1], 300_000))
        assertEquals("Resume The Third · 05:00", SeriesProgress.resumeLabel(meta, null, 300_000))
        assertEquals("Resume", SeriesProgress.resumeLabel(null, null, null))
    }

    @Test
    fun seasonNumberFallbacks() {
        assertEquals(4, SeriesProgress.seasonNumber(MediaItem(number = 4, name = "Season 9"), 0))
        assertEquals(2, SeriesProgress.seasonNumber(MediaItem(name = "Season 2"), 0))
        assertEquals(3, SeriesProgress.seasonNumber(MediaItem(name = "Specials"), 2))
        assertEquals(1, SeriesProgress.seasonNumber(null, 0))
    }

    @Test
    fun detailMergeKeepsListTitleAndFillsGaps() {
        val list = MediaItem(id = "m1", name = "Real Title", screenshotUri = "/p.jpg")
        val file = MediaItem(id = "m1", name = "1080p", description = "Plot", director = "Someone", screenshotUri = "/other.jpg")
        val merged = list.mergedWith(file)
        assertEquals("Real Title", merged.displayTitle)
        assertEquals("Plot", merged.overview)
        assertEquals("Someone", merged.director)
        assertEquals("/p.jpg", merged.screenshotUri)
    }

    @Test
    fun `resume label uses the picked season, not the Stalker episode selector`() {
        // Stalker: S3E2 arrives with series_number = 2 (the episode selector for movie-link).
        val ep = MediaItem(id = "e2", name = "Episode 2. Gold Medal", seriesNumber = 2, isEpisode = true)
        assertEquals("Resume S3E2 · 01:00", SeriesProgress.resumeLabel(null, ep, 60_000, seasonNumber = 3, episodeNumber = 2))
    }
}
