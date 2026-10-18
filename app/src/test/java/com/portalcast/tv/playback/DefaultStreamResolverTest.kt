package com.portalcast.tv.playback

import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.net.ApiService
import com.portalcast.tv.data.net.AppJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

/**
 * Drives the real Retrofit [ApiService] against a MockWebServer so the query
 * encoding is the one the server will see. The "server origin" used for prefixing
 * is deliberately different from the mock's address to prove the prefix comes from
 * the session store, not from the HTTP client.
 */
class DefaultStreamResolverTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ApiService
    private lateinit var resolver: DefaultStreamResolver
    private val origin = "http://10.0.0.5:3000"

    private val movieItem = MediaItem(id = "m1", name = "Inception", genresStr = "Sci-Fi, Thriller", categoryId = "12")
    private val seriesItem = MediaItem(id = "s9", name = "Dark", categoryId = "44")
    private val episodeItem = MediaItem(id = "e3", name = "Secrets", seriesNumber = 2, episodeNum = 3)

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()
        resolver = DefaultStreamResolver(api) { origin }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    }

    @Test
    fun `movie sends id title category and no series, prefixes relative cmd, progressive`() = runTest {
        enqueueJson("""{"js":{"cmd":"/api/proxy?t=tok123","other":1}}""")
        val session = PlaybackSession.Movie(item = movieItem, fileId = "file-77", category = "12")

        val result = resolver.resolve(session).getOrThrow()

        assertEquals("$origin/api/proxy?t=tok123", result.url)
        assertFalse(result.isHls)
        val req = server.takeRequest()
        val url = req.requestUrl!!
        assertEquals("/api/v2/movie-link", url.encodedPath)
        assertEquals("file-77", url.queryParameter("id"))
        assertEquals("Inception", url.queryParameter("title"))
        assertEquals("Sci-Fi, Thriller", url.queryParameter("category"))
        assertNull("movies must omit series", url.queryParameter("series"))
    }

    @Test
    fun `episode sends series number and combined label, detects hls`() = runTest {
        enqueueJson("""{"js":{"cmd":"/api/proxy?t=abc&m3u8=1"}}""")
        val session = PlaybackSession.Episode(
            series = seriesItem, seasonId = "sea2", episode = episodeItem, fileId = "ef-5",
            siblings = listOf(episodeItem), category = "44",
        )

        val result = resolver.resolve(session).getOrThrow()

        assertEquals("$origin/api/proxy?t=abc&m3u8=1", result.url)
        assertTrue(result.isHls)
        val url = server.takeRequest().requestUrl!!
        assertEquals("ef-5", url.queryParameter("id"))
        assertEquals("2", url.queryParameter("series"))
        assertEquals("Dark - Secrets", url.queryParameter("title"))
        assertEquals("44", url.queryParameter("category"))
    }

    @Test
    fun `top-level cmd fallback and absolute cmd kept as is`() = runTest {
        enqueueJson("""{"cmd":"https://cdn.example.com/x/stream.m3u8"}""")
        val result = resolver.resolve(PlaybackSession.Movie(movieItem, "f1", null)).getOrThrow()
        assertEquals("https://cdn.example.com/x/stream.m3u8", result.url)
        assertTrue(result.isHls)
    }

    @Test
    fun `missing cmd is Malformed, 401 is Unauthorized`() = runTest {
        enqueueJson("""{"js":{"error":"nope"}}""")
        val missing = resolver.resolve(PlaybackSession.Movie(movieItem, "f1", null)).exceptionOrNull()
        assertTrue("got $missing", missing is ApiError.Malformed)

        enqueueJson("""{"error":"Unauthorized"}""", code = 401)
        val unauthorized = resolver.resolve(PlaybackSession.Movie(movieItem, "f2", null)).exceptionOrNull()
        assertTrue("got $unauthorized", unauthorized is ApiError.Unauthorized)
    }

    @Test
    fun `second resolve is served from cache, forceRefresh re-requests`() = runTest {
        val session = PlaybackSession.Movie(movieItem, "f1", null)
        enqueueJson("""{"js":{"cmd":"/api/proxy?t=first"}}""")
        enqueueJson("""{"js":{"cmd":"/api/proxy?t=second"}}""")

        assertEquals("$origin/api/proxy?t=first", resolver.resolve(session).getOrThrow().url)
        assertEquals("$origin/api/proxy?t=first", resolver.resolve(session).getOrThrow().url)
        assertEquals("only one request so far", 1, server.requestCount)

        assertEquals("$origin/api/proxy?t=second", resolver.resolve(session, forceRefresh = true).getOrThrow().url)
        assertEquals(2, server.requestCount)
        // the refreshed URL replaces the cached one
        assertEquals("$origin/api/proxy?t=second", resolver.resolve(session).getOrThrow().url)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `failed resolve is not cached`() = runTest {
        val session = PlaybackSession.Movie(movieItem, "f1", null)
        enqueueJson("""{}""")
        assertTrue(resolver.resolve(session).isFailure)
        enqueueJson("""{"js":{"cmd":"/api/proxy?t=ok"}}""")
        assertEquals("$origin/api/proxy?t=ok", resolver.resolve(session).getOrThrow().url)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `live uses channel cmd as hls without any request`() = runTest {
        val channel = Channel(id = "101", name = "BBC One", cmd = "$origin/live.m3u8?t=xyz&id=101&proxy=1")
        val result = resolver.resolve(PlaybackSession.Live(channel, listOf(channel))).getOrThrow()
        assertEquals(channel.cmd, result.url)
        assertTrue(result.isHls)
        assertEquals(0, server.requestCount)

        val broken = Channel(id = "102", name = "Dead", cmd = null)
        val failure = resolver.resolve(PlaybackSession.Live(broken, listOf(broken))).exceptionOrNull()
        assertTrue("got $failure", failure is ApiError.Malformed)
    }

    @Test
    fun `no server configured fails with NoServer`() = runTest {
        val noServer = DefaultStreamResolver(api) { null }
        val failure = noServer.resolve(PlaybackSession.Movie(movieItem, "f1", null)).exceptionOrNull()
        assertTrue("got $failure", failure is ApiError.NoServer)
    }
}
