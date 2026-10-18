package com.portalcast.tv.data.repo

import com.portalcast.tv.data.TestStack
import com.portalcast.tv.data.fixtureResponse
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.query
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SeriesRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    @Test
    fun `groups prepend All once even when the server already has a star group`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/series-groups", "series-groups")
        val groups = stack.series.groups().getOrThrow()
        assertEquals(listOf("*", "3", "4"), groups.map { it.id })
        assertEquals("All", groups[0].displayTitle)
    }

    @Test
    fun `series list sends category page and sort`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/series", "series-page")
        val page = stack.series.series(category = "3", page = 1, sort = "alphabetic").getOrThrow()
        val request = stack.routes.requests("GET", "/api/v2/series").single()
        assertEquals("3", request.query("category"))
        assertEquals("1", request.query("page"))
        assertEquals("alphabetic", request.query("sort"))
        assertEquals("1", request.query("pageAtaTime"))
        assertEquals(true, page.data.single().isSeries)
        assertEquals("77", page.data.single().seriesId)
    }

    @Test
    fun `seasons retry with star when the category yields nothing`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/series") { req ->
            if (req.query("category") == "*") fixtureResponse("seasons") else fixtureResponse("empty-page")
        }

        val seasons = stack.series.seasons("77", "3").getOrThrow()

        assertEquals(listOf("sea1", "sea2"), seasons.map { it.id })
        assertEquals(true, seasons[1].isSeason)
        assertEquals(2, seasons[1].number)
        val requests = stack.routes.requests("GET", "/api/v2/series")
        assertEquals(listOf("3", "*"), requests.map { it.query("category") })
        assertTrue(requests.all { it.query("movieId") == "77" && it.query("seasonId") == null && it.query("page") == null })
    }

    @Test
    fun `seasons with a blank category go straight to star and an empty answer is not an error`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/series", "empty-page")
        val seasons = stack.series.seasons("77", "  ").getOrThrow()
        assertTrue(seasons.isEmpty())
        assertEquals(1, stack.routes.count("GET", "/api/v2/series"))
        assertEquals("*", stack.routes.requests("GET", "/api/v2/series").single().query("category"))
    }

    @Test
    fun `episodes send movieId seasonId page and category`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/series", "episodes")

        val page = stack.series.episodes("77", "sea1", 2, null).getOrThrow()

        val request = stack.routes.requests("GET", "/api/v2/series").single()
        assertEquals("77", request.query("movieId"))
        assertEquals("sea1", request.query("seasonId"))
        assertEquals("2", request.query("page"))
        assertEquals("*", request.query("category"))
        assertEquals(2, page.data.size)
        assertEquals(true, page.data[0].isEpisode)
        assertEquals(1, page.data[1].seriesNumber)
        assertEquals(2, page.data[1].episodeNum)
        assertEquals("44", page.data[1].time)
    }

    @Test
    fun `resolveEpisodeFile uses the movies route with category star and takes data zero`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/movies", "episode-file")

        val file = stack.series.resolveEpisodeFile("77", "sea1", "ep1").getOrThrow()

        assertEquals("file-ep1", file.id)
        assertEquals(1, file.seriesNumber)
        val request = stack.routes.requests("GET", "/api/v2/movies").single()
        assertEquals("77", request.query("movieId"))
        assertEquals("sea1", request.query("seasonId"))
        assertEquals("ep1", request.query("episodeId"))
        assertEquals("*", request.query("category"))
        assertNull(request.query("page"))
        assertEquals(0, stack.routes.count("GET", "/api/v2/series"))

        stack.routes.get("/api/v2/movies", "empty-page")
        assertTrue(stack.series.resolveEpisodeFile("77", "sea1", "nope").exceptionOrNull() is ApiError.Malformed)
    }
}
