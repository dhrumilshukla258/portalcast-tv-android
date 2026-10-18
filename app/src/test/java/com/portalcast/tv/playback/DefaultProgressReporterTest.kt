package com.portalcast.tv.playback

import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.Preferences
import com.portalcast.tv.data.model.ProgressRecord
import com.portalcast.tv.data.repo.UserRepository
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DefaultProgressReporterTest {

    private class FakeUserRepository : UserRepository {
        val saved = mutableListOf<ProgressRecord>()
        var rows: List<ProgressRecord> = emptyList()
        override suspend fun providerKey(): Result<String> = Result.success("stalker_test")
        override suspend fun preferences(forceRefresh: Boolean): Result<Preferences> = Result.success(Preferences())
        override suspend fun updatePreferences(patch: Preferences): Result<Preferences> = Result.success(patch)
        override suspend fun progress(forceRefresh: Boolean): Result<List<ProgressRecord>> = Result.success(rows)
        override suspend fun saveProgress(record: ProgressRecord): Result<Unit> { saved += record; return Result.success(Unit) }
        override suspend fun deleteProgress(mediaId: String): Result<Unit> = Result.success(Unit)
        override suspend fun continueWatching(): Result<List<ProgressRecord>> = Result.success(rows)
    }

    private val movieItem = MediaItem(id = "m1", name = "Inception", screenshotUri = "/misc/m1.jpg", categoryId = "12")
    private val movie = PlaybackSession.Movie(item = movieItem, fileId = "file-77", category = "12")

    private val seriesItem = MediaItem(id = "s9", name = "Dark", screenshotUri = "/misc/s9.jpg", categoryId = "44")
    private val ep3 = MediaItem(id = "e3", name = "Secrets", seriesNumber = 2, episodeNum = 3)
    private val ep4 = MediaItem(id = "e4", name = "Lies", seriesNumber = 2, episodeNum = 4)
    private val episode = PlaybackSession.Episode(
        series = seriesItem, seasonId = "sea2", episode = ep3, fileId = "ef-5", siblings = listOf(ep3, ep4), category = "44",
    )

    private val channel = Channel(id = "101", name = "BBC One", cmd = "http://x/live.m3u8?t=1")
    private val live = PlaybackSession.Live(channel, listOf(channel))

    private val hour = 60 * 60 * 1000L

    @Test
    fun `saves at most every 30 s while playing`() = runTest {
        val repo = FakeUserRepository()
        var now = 1_000_000L
        val reporter = DefaultProgressReporter(repo, { true }, this) { now }

        reporter.onProgress(movie, 5_000, hour); runCurrent()          // first tick starts the clock
        now += 10_000; reporter.onProgress(movie, 15_000, hour); runCurrent()
        assertEquals("nothing before 30 s", 0, repo.saved.size)

        now += 20_000; reporter.onProgress(movie, 35_000, hour); runCurrent()
        assertEquals(1, repo.saved.size)
        assertEquals("m1", repo.saved[0].mediaId)
        assertEquals(35.0, repo.saved[0].progress, 0.001)
        assertFalse(repo.saved[0].completed)

        now += 15_000; reporter.onProgress(movie, 50_000, hour); runCurrent()
        assertEquals("still one save at 45 s", 1, repo.saved.size)

        now += 15_000; reporter.onProgress(movie, 65_000, hour); runCurrent()
        assertEquals(2, repo.saved.size)
        assertEquals(65.0, repo.saved[1].progress, 0.001)
    }

    @Test
    fun `skip rules - position under 2 s, no duration, 90 percent or more`() = runTest {
        val repo = FakeUserRepository()
        var now = 0L
        val reporter = DefaultProgressReporter(repo, { true }, this) { now }
        reporter.onProgress(movie, 0, hour)

        now += 30_000; reporter.onProgress(movie, 2_000, hour); runCurrent()
        now += 30_000; reporter.onProgress(movie, 1_000_000, 0L); runCurrent()
        now += 30_000; reporter.onProgress(movie, (hour * 0.9).toLong(), hour); runCurrent()
        now += 30_000; reporter.onProgress(movie, hour - 1_000, hour); runCurrent()
        assertEquals(0, repo.saved.size)

        reporter.flush(movie, 2_000, hour)
        reporter.flush(movie, 50_000, 0L)
        reporter.flush(movie, (hour * 0.95).toLong(), hour)
        assertEquals("flush obeys the same skip rules", 0, repo.saved.size)

        now += 30_000; reporter.onProgress(movie, 2_001, hour); runCurrent()
        assertEquals("just over 2 s is savable", 1, repo.saved.size)
    }

    @Test
    fun `movie meta mirrors the webui shape`() = runTest {
        val repo = FakeUserRepository()
        val reporter = DefaultProgressReporter(repo, { true }, this) { 1_700_000_000_000L }
        reporter.flush(movie, 900_000, hour)

        val row = repo.saved.single()
        val meta = row.parsedMeta!!
        assertEquals("m1", meta.id)
        assertEquals("file-77", meta.playbackFileId)
        assertEquals("m1", meta.mediaId)
        assertEquals("m1", meta.itemId)
        assertNull(meta.seasonId)
        assertEquals("12", meta.categoryId)
        assertEquals("movie", meta.type)
        assertEquals("Inception", meta.title)
        assertEquals("Inception", meta.name)
        assertEquals(900.0, meta.currentTime!!, 0.001)
        assertEquals(3600.0, meta.duration!!, 0.001)
        assertEquals(25, meta.progressPercent)
        assertEquals(1_700_000_000_000L, meta.timestamp)
        assertEquals("/misc/m1.jpg", meta.screenshot_uri)
        assertEquals(0, meta.is_series)
        assertNull(meta.cmd)
        assertNull(meta.series_number)
        assertEquals("movie_m1", meta.catalogId)
        assertFalse("cmd must not be written", row.meta.containsKey("cmd"))
    }

    @Test
    fun `episode writes two rows with identical meta keyed by file id and series id`() = runTest {
        val repo = FakeUserRepository()
        val reporter = DefaultProgressReporter(repo, { true }, this) { 42L }
        reporter.flush(episode, 600_000, 2_400_000)

        assertEquals(2, repo.saved.size)
        assertEquals(setOf("ef-5", "s9"), repo.saved.map { it.mediaId }.toSet())
        assertEquals(repo.saved[0].meta, repo.saved[1].meta)
        repo.saved.forEach { assertEquals(600.0, it.progress, 0.001); assertFalse(it.completed) }

        val meta = repo.saved[0].parsedMeta!!
        assertEquals("ef-5", meta.id)
        assertEquals("ef-5", meta.playbackFileId)
        assertEquals("s9", meta.mediaId)
        assertEquals("ef-5", meta.itemId)
        assertEquals("sea2", meta.seasonId)
        assertEquals("44", meta.categoryId)
        assertEquals("series", meta.type)
        assertEquals("Dark", meta.title)
        assertEquals("Dark", meta.name)
        assertEquals("Secrets", meta.episodeTitle)
        assertEquals("/misc/s9.jpg", meta.screenshot_uri)
        assertEquals(1, meta.is_series)
        assertEquals(2, meta.series_number)
        assertEquals(25, meta.progressPercent)
        assertEquals("series_s9", meta.catalogId)
    }

    @Test
    fun `existing catalogId is preserved and discover-prefixed ids are stripped`() = runTest {
        val repo = FakeUserRepository()
        val reporter = DefaultProgressReporter(repo, { true }, this)
        reporter.flush(PlaybackSession.Movie(movieItem.copy(catalogId = "movie_999"), "f", null), 60_000, hour)
        assertEquals("movie_999", repo.saved.single().parsedMeta!!.catalogId)

        repo.saved.clear()
        reporter.flush(PlaybackSession.Movie(MediaItem(id = "movie_55", name = "X"), "f", null), 60_000, hour)
        assertEquals("movie_55", repo.saved.single().parsedMeta!!.catalogId)
    }

    @Test
    fun `flush saves immediately and restarts the 30 s clock`() = runTest {
        val repo = FakeUserRepository()
        var now = 0L
        val reporter = DefaultProgressReporter(repo, { true }, this) { now }
        reporter.onProgress(movie, 3_000, hour)
        now += 25_000
        reporter.flush(movie, 28_000, hour)
        assertEquals(1, repo.saved.size)

        now += 10_000; reporter.onProgress(movie, 38_000, hour); runCurrent()
        assertEquals("35 s after start but only 10 s after flush", 1, repo.saved.size)
        now += 20_000; reporter.onProgress(movie, 58_000, hour); runCurrent()
        assertEquals(2, repo.saved.size)
    }

    @Test
    fun `markCompleted writes completed rows with progress equal to duration`() = runTest {
        val repo = FakeUserRepository()
        val reporter = DefaultProgressReporter(repo, { true }, this)

        reporter.markCompleted(movie, hour)
        val m = repo.saved.single()
        assertEquals("m1", m.mediaId)
        assertTrue(m.completed)
        assertEquals(3600.0, m.progress, 0.001)
        assertEquals(100, m.parsedMeta!!.progressPercent)

        repo.saved.clear()
        reporter.markCompleted(episode, 2_400_000)
        assertEquals(setOf("ef-5", "s9"), repo.saved.map { it.mediaId }.toSet())
        repo.saved.forEach { assertTrue(it.completed); assertEquals(2400.0, it.progress, 0.001) }
    }

    @Test
    fun `resume position follows the greater-than-2s-and-not-completed rule`() = runTest {
        val repo = FakeUserRepository()
        val reporter = DefaultProgressReporter(repo, { true }, this)

        repo.rows = listOf(ProgressRecord(mediaId = "m1", progress = 120.5, completed = false))
        assertEquals(120_500L, reporter.resumePositionMs(movie))

        repo.rows = listOf(ProgressRecord(mediaId = "m1", progress = 120.5, completed = true))
        assertNull("completed rows do not resume", reporter.resumePositionMs(movie))

        repo.rows = listOf(ProgressRecord(mediaId = "m1", progress = 2.0, completed = false))
        assertNull("2 s is not resumable", reporter.resumePositionMs(movie))

        repo.rows = emptyList()
        assertNull(reporter.resumePositionMs(movie))
    }

    @Test
    fun `episode resumes from the file row, not the series row`() = runTest {
        val repo = FakeUserRepository()
        val reporter = DefaultProgressReporter(repo, { true }, this)
        repo.rows = listOf(
            ProgressRecord(mediaId = "s9", progress = 999.0, completed = false),
            ProgressRecord(mediaId = "ef-5", progress = 300.0, completed = false),
        )
        assertEquals(300_000L, reporter.resumePositionMs(episode))

        repo.rows = listOf(ProgressRecord(mediaId = "s9", progress = 999.0, completed = false))
        assertNull("the series row alone belongs to some other episode", reporter.resumePositionMs(episode))
    }

    @Test
    fun `live never saves, completes or resumes`() = runTest {
        val repo = FakeUserRepository()
        var now = 0L
        val reporter = DefaultProgressReporter(repo, { true }, this) { now }
        reporter.onProgress(live, 10_000, hour)
        now += 60_000; reporter.onProgress(live, 70_000, hour); runCurrent()
        reporter.flush(live, 70_000, hour)
        reporter.markCompleted(live, hour)
        repo.rows = listOf(ProgressRecord(mediaId = "101", progress = 500.0))
        assertNull(reporter.resumePositionMs(live))
        assertEquals(0, repo.saved.size)
    }

    @Test
    fun `signed-out device and blank titles write nothing`() = runTest {
        val repo = FakeUserRepository()
        val signedOut = DefaultProgressReporter(repo, { false }, this)
        signedOut.flush(movie, 60_000, hour)
        signedOut.markCompleted(movie, hour)
        assertEquals(0, repo.saved.size)

        val reporter = DefaultProgressReporter(repo, { true }, this)
        reporter.flush(PlaybackSession.Movie(MediaItem(id = "m2"), "f", null), 60_000, hour)
        assertEquals("blank title would render as ?? on the web", 0, repo.saved.size)
    }

    @Test
    fun `switching titles restarts the cadence for the new title`() = runTest {
        val repo = FakeUserRepository()
        var now = 0L
        val reporter = DefaultProgressReporter(repo, { true }, this) { now }
        reporter.onProgress(movie, 3_000, hour)
        now += 29_000
        reporter.onProgress(episode, 3_000, hour); runCurrent()   // new title: clock restarts
        now += 2_000; reporter.onProgress(episode, 5_000, hour); runCurrent()
        assertEquals(0, repo.saved.size)
        now += 28_000; reporter.onProgress(episode, 33_000, hour); runCurrent()
        assertEquals(2, repo.saved.size)
        assertNotNull(repo.saved.firstOrNull { it.mediaId == "ef-5" })
    }
}
