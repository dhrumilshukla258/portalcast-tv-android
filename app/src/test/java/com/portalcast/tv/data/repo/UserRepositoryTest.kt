package com.portalcast.tv.data.repo

import com.portalcast.tv.data.TestStack
import com.portalcast.tv.data.bodyJson
import com.portalcast.tv.data.fixture
import com.portalcast.tv.data.json
import com.portalcast.tv.data.model.Preferences
import com.portalcast.tv.data.model.ProgressMeta
import com.portalcast.tv.data.model.ProgressRecord
import com.portalcast.tv.data.model.UserDto
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.net.AppJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    /** Server-side shallow merge of the profile fixture's preferences with the PUT body. */
    private fun mergedPreferencesResponse(request: RecordedRequest) =
        json(AppJson.encodeToString(JsonObject.serializer(), JsonObject(mapOf(
            "success" to AppJson.parseToJsonElement("true"),
            "preferences" to JsonObject(profilePreferences() + request.bodyJson()),
        ))))

    private fun profilePreferences(): JsonObject =
        AppJson.parseToJsonElement(fixture("profile")).jsonObject["preferences"]!!.jsonObject

    @Test
    fun `providerKey falls back to stalker_unknown on 404 and is cached for the session`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/provider-key") { json("""{"error":"Not Found"}""", 404) }

        assertEquals("stalker_unknown", stack.users.providerKey().getOrThrow())
        assertEquals("stalker_unknown", stack.users.providerKey().getOrThrow())
        assertEquals(1, stack.routes.count("GET", "/api/v2/provider-key"))

        // A new login must not see the old answer.
        stack.session.saveSession("access-2", "refresh-2", UserDto(id = 9, email = "other@example.com"))
        stack.routes.get("/api/v2/provider-key", "provider-key")
        assertEquals("xtream_host", stack.users.providerKey().getOrThrow())
        assertEquals(2, stack.routes.count("GET", "/api/v2/provider-key"))
    }

    @Test
    fun `providerKey does not cache transport failures`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/provider-key") { json("""{"error":"boom"}""", 500) }
        val failed = stack.users.providerKey()
        assertTrue(failed.exceptionOrNull() is ApiError.Http)
        assertEquals(500, (failed.exceptionOrNull() as ApiError.Http).code)

        stack.routes.get("/api/v2/provider-key", "provider-key")
        assertEquals("xtream_host", stack.users.providerKey().getOrThrow())
    }

    @Test
    fun `preferences come from the profile, ignore unknown keys and are cached`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/user/profile", "profile")

        val prefs = stack.users.preferences().getOrThrow()
        assertEquals(listOf("1", "2"), prefs.favorites)
        assertEquals(mapOf("other_pk" to "9"), prefs.lastSelectedTvGroup)
        assertEquals("contain", prefs.videoFitMode)

        stack.users.preferences().getOrThrow()
        assertEquals(1, stack.routes.count("GET", "/api/user/profile"))
        stack.users.preferences(forceRefresh = true).getOrThrow()
        assertEquals(2, stack.routes.count("GET", "/api/user/profile"))
    }

    @Test
    fun `updatePreferences sends only the patch and caches the server's merged reply`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/user/profile", "profile")
        stack.routes.on("PUT", "/api/user/preferences") { mergedPreferencesResponse(it) }

        val merged = stack.users.updatePreferences(Preferences(preferredContentType = "tv", recentSearches = listOf("bbc"))).getOrThrow()

        val body = stack.routes.requests("PUT", "/api/user/preferences").single().bodyJson()
        assertEquals(setOf("preferredContentType", "recentSearches"), body.keys)
        assertEquals("tv", merged.preferredContentType)
        assertEquals(listOf("bbc"), merged.recentSearches)
        assertEquals("server fields kept", listOf("1", "2"), merged.favorites)

        assertEquals(listOf("1", "2"), stack.users.preferences().getOrThrow().favorites)
        assertEquals("cache primed by the PUT reply", 0, stack.routes.count("GET", "/api/user/profile"))
    }

    @Test
    fun `updatePreferences merges locally when the server omits preferences`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/user/profile", "profile")
        stack.routes.on("PUT", "/api/user/preferences") { json("""{"success":true}""") }
        stack.users.preferences().getOrThrow()

        val merged = stack.users.updatePreferences(Preferences(favorites = listOf("7"))).getOrThrow()

        assertEquals(listOf("7"), merged.favorites)
        assertEquals(listOf("5", "1"), merged.recentChannels)
        assertEquals(listOf("7"), stack.users.preferences().getOrThrow().favorites)
    }

    @Test
    fun `progress is cached for four seconds and invalidated by writes`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/user/progress", "progress")
        stack.routes.on("PUT", "/api/user/progress") { json("""{"success":true}""") }
        stack.routes.on("DELETE", "/api/user/progress/a%2Fb") { json("""{"success":true}""") }

        val rows = stack.users.progress().getOrThrow()
        assertEquals(8, rows.size)
        assertEquals(44.5, rows.last().progress, 0.0001)
        assertEquals(false, rows.last().completed)
        stack.users.progress().getOrThrow()
        assertEquals(1, stack.routes.count("GET", "/api/user/progress"))

        stack.clock.addAndGet(DefaultUserRepository.PROGRESS_TTL_MS - 1)
        stack.users.progress().getOrThrow()
        assertEquals("still inside the TTL", 1, stack.routes.count("GET", "/api/user/progress"))

        stack.clock.addAndGet(2)
        stack.users.progress().getOrThrow()
        assertEquals("expired", 2, stack.routes.count("GET", "/api/user/progress"))

        val meta = ProgressMeta(id = "m1", mediaId = "m1", itemId = "m1", type = "movie", title = "Film One", currentTime = 700.0, duration = 6000.0, progressPercent = 12, timestamp = 1L, catalogId = "movie_m1")
        stack.users.saveProgress(ProgressRecord(mediaId = "m1", progress = 700.0, completed = false, meta = meta.toJson())).getOrThrow()
        val put = stack.routes.requests("PUT", "/api/user/progress").single().bodyJson()
        assertEquals("m1", put["mediaId"]?.jsonPrimitive?.content)
        assertEquals("700.0", put["progress"]?.jsonPrimitive?.content)
        assertEquals("false", put["completed"]?.jsonPrimitive?.content)
        assertEquals("movie_m1", put["meta"]?.jsonObject?.get("catalogId")?.jsonPrimitive?.content)
        assertNull("updatedAt is never sent", put["updatedAt"])
        stack.users.progress().getOrThrow()
        assertEquals("write invalidates", 3, stack.routes.count("GET", "/api/user/progress"))

        stack.users.deleteProgress("a/b").getOrThrow()
        assertEquals(1, stack.routes.count("DELETE", "/api/user/progress/a%2Fb"))
        stack.users.progress().getOrThrow()
        assertEquals(4, stack.routes.count("GET", "/api/user/progress"))
    }

    @Test
    fun `continueWatching drops tv and completed rows, sorts newest first and dedupes`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/user/progress", "progress")

        val rows = stack.users.continueWatching().getOrThrow()

        val keys = rows.map { it.parsedMeta?.id?.takeIf { k -> k.isNotBlank() } ?: it.mediaId }
        assertEquals(listOf("s1", "m1", "m5"), keys)
        val episode = rows.first()
        assertEquals("the episode row and the series row collapse into one", "e1", episode.mediaId)
        assertEquals("series_s1", episode.parsedMeta?.catalogId)
        assertEquals(1, episode.parsedMeta?.is_series)
        assertEquals(1, episode.parsedMeta?.series_number)
    }

    @Test
    fun `selectContinueWatching handles an empty list and rows without meta`() {
        assertTrue(DefaultUserRepository.selectContinueWatching(emptyList()).isEmpty())
        val bare = listOf(ProgressRecord(mediaId = "x"), ProgressRecord(mediaId = "x"), ProgressRecord(mediaId = ""))
        assertEquals(listOf("x"), DefaultUserRepository.selectContinueWatching(bare).map { it.mediaId })
    }

    @Test
    fun `preferences patch encoding keeps whole maps`() {
        val patch = Preferences(lastSelectedTvGroup = mapOf("xtream_host" to "7", "other" to "1")).toPatch()
        assertEquals(setOf("lastSelectedTvGroup"), patch.keys)
        assertEquals(2, patch["lastSelectedTvGroup"]!!.jsonObject.size)
        val list = Preferences(favorites = emptyList()).toPatch()
        assertEquals("an emptied list is still sent", 0, list["favorites"]!!.jsonArray.size)
    }
}
