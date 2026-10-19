package com.portalcast.tv.data.repo

import com.portalcast.tv.data.TestStack
import com.portalcast.tv.data.bodyJson
import com.portalcast.tv.data.fixture
import com.portalcast.tv.data.json
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.net.AppJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LiveRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private fun echoPreferences(request: RecordedRequest): MockResponse {
        val current = AppJson.parseToJsonElement(fixture("profile")).jsonObject["preferences"]!!.jsonObject
        val merged = JsonObject(current + request.bodyJson())
        return json(AppJson.encodeToString(JsonObject.serializer(), JsonObject(mapOf(
            "success" to AppJson.parseToJsonElement("true"), "preferences" to merged,
        ))))
    }

    @Test
    fun `groups drop the portal's own all groups and are cached`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/groups", "groups")

        val groups = stack.live.groups().getOrThrow()

        assertEquals(listOf("7", "8", "vcat_12"), groups.map { it.id })
        assertEquals("Sports", groups[1].displayTitle)
        assertEquals(2, groups[1].number)
        stack.live.groups().getOrThrow()
        assertEquals(1, stack.routes.count("GET", "/api/v2/groups"))
        stack.live.groups(forceRefresh = true).getOrThrow()
        assertEquals(2, stack.routes.count("GET", "/api/v2/groups"))
    }

    @Test
    fun `cold cache 302 on groups maps to ColdCache and is not followed`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/groups") {
            MockResponse().setResponseCode(302).addHeader("Location", "${stack.origin}/api/v2/refresh-groups")
        }

        val result = stack.live.groups()

        assertTrue(result.exceptionOrNull() is ApiError.ColdCache)
        assertEquals(0, stack.routes.count("GET", "/api/v2/refresh-groups"))
        assertEquals(1, stack.routes.received.size)
    }

    @Test
    fun `cold cache 403 on list routes maps to ColdCache but 403 elsewhere stays Forbidden`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/movie-groups") { json("""{"error":"Forbidden"}""", 403) }
        stack.routes.get("/api/v2/series-groups") { json("""{"error":"Forbidden"}""", 403) }
        stack.routes.get("/api/v2/channels") { json("""{"error":"Forbidden"}""", 403) }
        stack.routes.get("/api/v2/movies") { json("""{"error":"Forbidden"}""", 403) }

        assertTrue(stack.vod.groups().exceptionOrNull() is ApiError.ColdCache)
        assertTrue(stack.series.groups().exceptionOrNull() is ApiError.ColdCache)
        assertTrue(stack.live.channels().exceptionOrNull() is ApiError.ColdCache)
        val movies = stack.vod.movies("*", 1).exceptionOrNull()
        assertTrue(movies is ApiError.Forbidden)
        assertEquals("Forbidden", movies!!.message)
    }

    @Test
    fun `channels are cached per session and forceRefresh bypasses`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/channels", "channels")

        val channels = stack.live.channels().getOrThrow()

        assertEquals(listOf("101", "102"), channels.map { it.id })
        assertEquals("7", channels[0].groupId)
        assertEquals("8", channels[1].groupId)
        assertEquals("/misc/logos/bbc.png", channels[0].artwork)
        assertTrue(channels[0].cmd!!.contains("proxy=1"))
        stack.live.channels().getOrThrow()
        assertEquals(1, stack.routes.count("GET", "/api/v2/channels"))
        stack.live.channels(forceRefresh = true).getOrThrow()
        assertEquals(2, stack.routes.count("GET", "/api/v2/channels"))

        stack.live.clearCaches()
        stack.live.channels().getOrThrow()
        assertEquals(3, stack.routes.count("GET", "/api/v2/channels"))
    }

    @Test
    fun `epg parses and is cached for thirty minutes while an empty array is null and not cached`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/epg") { json("[]") }
        assertNull(stack.live.epg().getOrThrow())
        assertNull(stack.live.epg().getOrThrow())
        assertEquals("cold answers are retried", 2, stack.routes.count("GET", "/api/v2/epg"))

        // The guide is windowed to now..now+6h while streaming (EpgStream): pin "now" to the fixture.
        stack.clock.set(1760783400L * 1000)
        stack.routes.get("/api/v2/epg", "epg")
        val epg = stack.live.epg().getOrThrow()!!
        val (now, next) = epg.nowNext("101", nowMs = 1760783400L * 1000)
        assertEquals("News", now?.name)
        assertEquals("Weather", next?.name)
        assertTrue(epg.forChannel("102").isEmpty())
        assertTrue(epg.forChannel("999").isEmpty())

        stack.live.epg().getOrThrow()
        assertEquals(3, stack.routes.count("GET", "/api/v2/epg"))
        stack.clock.addAndGet(DefaultLiveRepository.EPG_TTL_MS)
        stack.live.epg().getOrThrow()
        assertEquals(4, stack.routes.count("GET", "/api/v2/epg"))
    }

    @Test
    fun `toggleFavorite sends the whole list each time`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/user/profile", "profile")
        stack.routes.on("PUT", "/api/user/preferences") { echoPreferences(it) }

        assertEquals(listOf("1", "2"), stack.live.favorites().getOrThrow())

        val added = stack.live.toggleFavorite("3").getOrThrow()
        assertEquals(listOf("1", "2", "3"), added)
        val firstBody = stack.routes.requests("PUT", "/api/user/preferences")[0].bodyJson()
        assertEquals(setOf("favorites"), firstBody.keys)
        assertEquals(listOf("1", "2", "3"), firstBody["favorites"]!!.jsonArray.map { it.jsonPrimitive.content })

        val removed = stack.live.toggleFavorite("1").getOrThrow()
        assertEquals(listOf("2", "3"), removed)
        val secondBody = stack.routes.requests("PUT", "/api/user/preferences")[1].bodyJson()
        assertEquals(listOf("2", "3"), secondBody["favorites"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("favourites come from the cached preferences", 1, stack.routes.count("GET", "/api/user/profile"))
    }

    @Test
    fun `recordRecent writes whole maps keyed by provider and preserves other entries`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/user/profile", "profile")
        stack.routes.get("/api/v2/provider-key", "provider-key")
        stack.routes.on("PUT", "/api/user/preferences") { echoPreferences(it) }

        stack.live.recordRecent("1", "7")

        val body = stack.routes.requests("PUT", "/api/user/preferences").single().bodyJson()
        assertEquals(setOf("recentChannels", "lastSelectedCategory", "lastSelectedTvGroup", "lastSelectedTvChannel"), body.keys)
        assertEquals(listOf("1", "5"), body["recentChannels"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(mapOf("xtream_host" to "1"), body["lastSelectedTvChannel"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content })
        assertEquals(mapOf("other_pk" to "9", "xtream_host" to "7"), body["lastSelectedTvGroup"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content })
        assertEquals(mapOf("xtream_host_movie" to "3", "lastPlayedTvChannelId" to "1"), body["lastSelectedCategory"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content })
    }

    @Test
    fun `recordRecent caps at twenty newest first and omits the group when unknown`() = runBlocking {
        val stack = TestStack(server)
        val many = (100..130).map { "\"$it\"" }.joinToString(",")
        stack.routes.get("/api/user/profile") { json("""{"id":1,"email":"tv@example.com","preferences":{"recentChannels":[$many]}}""") }
        stack.routes.get("/api/v2/provider-key") { json("""{"error":"Not Found"}""", 404) }
        stack.routes.on("PUT", "/api/user/preferences") { echoPreferences(it) }

        stack.live.recordRecent("115", null)

        val body = stack.routes.requests("PUT", "/api/user/preferences").single().bodyJson()
        val recents = body["recentChannels"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(20, recents.size)
        assertEquals("115", recents.first())
        assertEquals(1, recents.count { it == "115" })
        assertEquals("100", recents[1])
        assertNull(body["lastSelectedTvGroup"])
        assertEquals(mapOf("stalker_unknown" to "115"), body["lastSelectedTvChannel"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content })
    }

    @Test
    fun `recordRecent swallows failures`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/user/profile", "profile")
        stack.routes.get("/api/v2/provider-key", "provider-key")
        stack.routes.on("PUT", "/api/user/preferences") { json("""{"error":"boom"}""", 500) }
        stack.live.recordRecent("1", "7")
        assertEquals(1, stack.routes.count("PUT", "/api/user/preferences"))
    }

    @Test
    fun `an unauthenticated list call fails as Unauthorized when the refresh is dead`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/channels") { json("""{"error":"Unauthorized"}""", 401) }
        stack.routes.on("POST", "/api/auth/refresh") { json("""{"error":"User is disabled or does not exist"}""", 401) }
        val result = stack.live.channels()
        assertTrue(result.exceptionOrNull() is ApiError.Unauthorized)
        assertNull(stack.session.session.value)
        assertEquals(1, stack.routes.count("POST", "/api/auth/refresh"))
    }
}
