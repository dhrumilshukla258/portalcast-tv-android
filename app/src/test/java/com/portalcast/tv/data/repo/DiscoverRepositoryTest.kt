package com.portalcast.tv.data.repo

import com.portalcast.tv.data.TestStack
import com.portalcast.tv.data.fixtureResponse
import com.portalcast.tv.data.model.DiscoverQuery
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.query
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiscoverRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    private suspend fun TestStack.browseItems(): List<MediaItem> {
        routes.get("/api/v2/discover/browse", "discover-browse")
        return discover.browse(DiscoverQuery(), 1).getOrThrow().data
    }

    @Test
    fun `browse sends comma joined genres and parses prefixed items`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/discover/browse", "discover-browse")

        val page = stack.discover.browse(DiscoverQuery(type = "movie", genres = listOf("Action", "Drama"), country = "US", language = ""), 3).getOrThrow()

        val request = stack.routes.requests("GET", "/api/v2/discover/browse").single()
        assertEquals("movie", request.query("type"))
        assertEquals("Action,Drama", request.query("genre"))
        assertEquals("US", request.query("country"))
        assertNull(request.query("language"))
        assertNull(request.query("theme"))
        assertEquals("3", request.query("page"))
        assertEquals(2, page.totalItems)
        assertEquals("movie_55", page.data[0].id)
        assertEquals("55", page.data[0].rawId)
        assertEquals("movie", page.data[0].discoverType)
        assertEquals("series", page.data[1].discoverType)
        assertEquals("3", page.data[1].category)
    }

    @Test
    fun `recommendations tolerate a missing basedOnTitle and facets parse string counts`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/discover/recommendations", "discover-recommendations")
        stack.routes.get("/api/v2/discover/facets", "discover-facets")

        val recs = stack.discover.recommendations("movie").getOrThrow()
        assertNull(recs.basedOnTitle)
        assertEquals("movie_56", recs.data.single().id)

        val facets = stack.discover.facets(null).getOrThrow()
        assertEquals(98, facets.genres[1].count)
        assertTrue(facets.themes.isEmpty())
        assertNull(stack.routes.requests("GET", "/api/v2/discover/facets").single().query("type"))
    }

    @Test
    fun `variants return the list under the prefixed id`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/discover/variants", "discover-variants")
        val variants = stack.discover.variants("movie_55").getOrThrow()
        assertEquals("movie_55", stack.routes.requests("GET", "/api/v2/discover/variants").single().query("id"))
        assertEquals(listOf("Hindi", "Tamil"), variants.map { it.variantLabel })
    }

    @Test
    fun `openItem for a movie retries with star, keeps Discover's title and stamps the working category`() = runBlocking {
        val stack = TestStack(server)
        val item = stack.browseItems()[0]
        stack.routes.get("/api/v2/movies") { req ->
            if (req.query("category") == "*") fixtureResponse("movie-file") else fixtureResponse("empty-page")
        }

        val resolved = stack.discover.openItem(item).getOrThrow()

        val requests = stack.routes.requests("GET", "/api/v2/movies")
        assertEquals(listOf("9", "*"), requests.map { it.query("category") })
        assertTrue(requests.all { it.query("movieId") == "55" })

        assertEquals("the file id, which movie-link wants", "55", resolved.id)
        assertEquals("The Film", resolved.title)
        assertEquals("The Film", resolved.name)
        assertEquals("The Film", resolved.displayTitle)
        assertEquals("*", resolved.categoryId)
        assertEquals("movie_55", resolved.catalogId)
        assertEquals("movie", resolved.type)
        assertEquals("poster from Discover, the file's is blank", "https://image.tmdb.org/t/p/w500/poster55.jpg", resolved.screenshotUri)
        assertEquals("Discover cast wins over the file's empty string", "A Actor, B Actor", resolved.actors)
        assertEquals("C Director", resolved.director)
        assertEquals("file fields come through", "mkv", resolved.containerExtension)
        assertEquals("A film about things.", resolved.overview)
        assertEquals("https://image.tmdb.org/t/p/w780/bd55.jpg", resolved.backdropPath)
        assertNotNull("cmd present marks the item as already resolved (never play it)", resolved.cmd)
    }

    @Test
    fun `openItem for a movie uses Discover's category when it works`() = runBlocking {
        val stack = TestStack(server)
        val item = stack.browseItems()[0]
        stack.routes.get("/api/v2/movies", "movie-file")

        val resolved = stack.discover.openItem(item).getOrThrow()

        assertEquals(1, stack.routes.count("GET", "/api/v2/movies"))
        assertEquals("9", stack.routes.requests("GET", "/api/v2/movies").single().query("category"))
        assertEquals("9", resolved.categoryId)
        assertEquals("the file's own category_id is overridden", "9", resolved.category)
    }

    @Test
    fun `openItem for a series validates existence and returns the series itself`() = runBlocking {
        val stack = TestStack(server)
        val item = stack.browseItems()[1]
        stack.routes.get("/api/v2/series") { req ->
            if (req.query("category") == "*") fixtureResponse("seasons") else fixtureResponse("empty-page")
        }

        val resolved = stack.discover.openItem(item).getOrThrow()

        assertEquals(listOf("3", "*"), stack.routes.requests("GET", "/api/v2/series").map { it.query("category") })
        assertTrue(stack.routes.requests("GET", "/api/v2/series").all { it.query("movieId") == "77" })
        assertEquals("77", resolved.id)
        assertEquals(true, resolved.isSeries)
        assertEquals("The Show", resolved.displayTitle)
        assertEquals("*", resolved.categoryId)
        assertEquals("series_77", resolved.catalogId)
        assertEquals("series", resolved.type)
        assertEquals("not a season", null, resolved.isSeason)
        assertEquals(0, stack.routes.count("GET", "/api/v2/movies"))
    }

    @Test
    fun `openItem fails as Malformed when neither lookup finds the title`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/v2/movies", "empty-page")
        stack.routes.get("/api/v2/series", "empty-page")

        val movie = stack.discover.openItem(MediaItem(id = "movie_404", type = "movie", title = "Lost", category = "2"))
        assertTrue(movie.exceptionOrNull() is ApiError.Malformed)
        assertEquals(2, stack.routes.count("GET", "/api/v2/movies"))

        val series = stack.discover.openItem(MediaItem(id = "series_404", title = "Lost"))
        assertTrue(series.exceptionOrNull() is ApiError.Malformed)
        assertEquals("no category means a single star lookup", 1, stack.routes.count("GET", "/api/v2/series"))
    }
}
