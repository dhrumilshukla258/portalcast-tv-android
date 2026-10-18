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

class VodRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    @Test
    fun `groups prepend the synthetic All group`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/movie-groups", "movie-groups")

        val groups = stack.vod.groups().getOrThrow()

        assertEquals(listOf("*", "1", "2"), groups.map { it.id })
        assertEquals("All", groups[0].displayTitle)
        assertEquals("Action", groups[1].displayTitle)
    }

    @Test
    fun `movies always send category and page with pageAtaTime 1`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/movies", "movies-page")

        val page = stack.vod.movies(category = "", page = 2).getOrThrow()

        val request = stack.routes.requests("GET", "/api/v2/movies").single()
        assertEquals("*", request.query("category"))
        assertEquals("2", request.query("page"))
        assertEquals("1", request.query("pageAtaTime"))
        assertNull(request.query("search"))
        assertNull(request.query("sort"))
        assertNull(request.query("movieId"))
        assertEquals(2, page.page)
        assertEquals(40, page.totalItems)
        assertTrue(page.hasMore(14))
        assertEquals(2, page.data.size)
        assertEquals("55", page.data[0].id)
        assertEquals("55", page.data[0].streamId)
        assertEquals("Action, Drama", page.data[0].labelCategory)
        assertNull("placeholder rating hidden", page.data[1].displayRating)
        assertNull("placeholder description hidden", page.data[1].overview)

        stack.vod.movies(category = "12", page = 1, search = "bat", sort = "latest").getOrThrow()
        val second = stack.routes.requests("GET", "/api/v2/movies")[1]
        assertEquals("12", second.query("category"))
        assertEquals("1", second.query("page"))
        assertEquals("bat", second.query("search"))
        assertEquals("latest", second.query("sort"))
    }

    @Test
    fun `resolveFile takes data zero and retries with star when the category is stale`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/movies") { req ->
            if (req.query("category") == "*") fixtureResponse("movie-file") else fixtureResponse("empty-page")
        }

        val file = stack.vod.resolveFile("55", "9").getOrThrow()

        assertEquals("55", file.id)
        assertEquals("Hindi / 1080p", file.name)
        val requests = stack.routes.requests("GET", "/api/v2/movies")
        assertEquals(2, requests.size)
        assertEquals(listOf("9", "*"), requests.map { it.query("category") })
        assertTrue(requests.all { it.query("movieId") == "55" && it.query("page") == null })
    }

    @Test
    fun `resolveFile uses star directly when no category is known and fails as Malformed when empty`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/movies", "movie-file")
        stack.vod.resolveFile("55", null).getOrThrow()
        assertEquals(listOf("*"), stack.routes.requests("GET", "/api/v2/movies").map { it.query("category") })

        stack.routes.get("/api/v2/movies", "empty-page")
        val missing = stack.vod.resolveFile("404", "*")
        assertTrue(missing.exceptionOrNull() is ApiError.Malformed)
        assertEquals("no second attempt when already star", 2, stack.routes.count("GET", "/api/v2/movies"))
    }
}
