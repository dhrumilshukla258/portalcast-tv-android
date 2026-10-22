package com.portalcast.tv.ui.discover

import androidx.lifecycle.viewModelScope
import com.portalcast.tv.data.model.DiscoverBrowseResponse
import com.portalcast.tv.data.model.DiscoverFacets
import com.portalcast.tv.data.model.DiscoverQuery
import com.portalcast.tv.data.model.FacetValue
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.Preferences
import com.portalcast.tv.data.model.ProgressMeta
import com.portalcast.tv.data.model.ProgressRecord
import com.portalcast.tv.data.model.RecommendationsResponse
import com.portalcast.tv.data.model.HomeResponse
import com.portalcast.tv.data.model.HomeRowDto
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.repo.DiscoverRepository
import com.portalcast.tv.data.repo.UserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun item(id: String, title: String = id) = MediaItem(id = id, title = title, type = if (id.startsWith("series_")) "series" else "movie")

    private fun page(vararg ids: String, total: Int = ids.size) =
        DiscoverBrowseResponse(data = ids.map { item(it) }, page = 1, totalItems = total)

    private fun record(mediaId: String, meta: ProgressMeta) = ProgressRecord(mediaId = mediaId, meta = meta.toJson())

    @Test
    fun `rows come in order and empty ones are hidden`() = runTest(dispatcher) {
        val repo = FakeDiscoverRepository().apply {
            recommendations = Result.success(RecommendationsResponse(data = listOf(item("movie_9")), basedOnTitle = "Heat"))
            whatsNew = Result.success(page("movie_1", "movie_2"))
            facets = Result.success(DiscoverFacets(genres = listOf(
                FacetValue("Drama", 50), FacetValue("Uncategorized", 99), FacetValue("Action", 70), FacetValue("Empty", 10),
            )))
            genrePages["Empty"] = Result.success(page())
        }
        val user = FakeUserRepository()
        val vm = DiscoverViewModel(repo, user)
        vm.onScreenShown()
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(
            listOf("recommended", "whats_new", "genre:Action", "genre:Drama", "genre:Empty"),
            s.rows.map { it.key },
        )
        assertEquals("Because you watched Heat", s.rows[0].title)
        assertEquals("What's new", s.rows[1].title)
        assertEquals(DiscoverQuery(type = null), repo.browseCalls.first().first)

        // genre rows are placeholders until they near the viewport
        assertTrue(s.rows.filter { it.kind == RowKind.Genre }.none { it.requested })
        s.rows.forEach { vm.onRowVisible(it.key) }
        advanceUntilIdle()
        assertEquals(listOf("recommended", "whats_new", "genre:Action", "genre:Drama"), vm.state.value.visibleRows.map { it.key })
        assertNull(vm.state.value.emptyHint)
    }

    @Test
    fun `the server's Home rows come first and genre rows follow the viewer's genres`() = runTest(dispatcher) {
        val repo = FakeDiscoverRepository().apply {
            home = Result.success(HomeResponse(
                rows = listOf(
                    HomeRowDto("because:movie_9", "because", "Because you watched Heat", listOf(item("movie_1"), item("movie_2"))),
                    HomeRowDto("picks", "picks", "Top picks for you", listOf(item("movie_3"))),
                ),
                genreOrder = listOf("Crime", "Not a facet", "Drama"),
            ))
            whatsNew = Result.success(page("movie_5"))
            facets = Result.success(DiscoverFacets(genres = listOf(
                FacetValue("Drama", 50), FacetValue("Action", 70), FacetValue("Crime", 10), FacetValue("Uncategorized", 99),
            )))
        }
        val vm = DiscoverViewModel(repo, FakeUserRepository())
        vm.onScreenShown(); advanceUntilIdle()

        assertEquals(
            listOf("home:because:movie_9", "home:picks", "whats_new", "genre:Crime", "genre:Drama", "genre:Action"),
            vm.state.value.rows.map { it.key },
        )
        assertEquals("Because you watched Heat", vm.state.value.rows[0].title)
        assertTrue(repo.recommendationTypes.isEmpty())
        // personal rows are complete: they never page
        vm.onRowNearEnd("home:picks"); advanceUntilIdle()
        assertEquals(1, repo.browseCalls.size)
    }

    @Test
    fun `the Movies tab shows one type and how many titles it has`() = runTest(dispatcher) {
        val repo = FakeDiscoverRepository().apply {
            home = Result.success(HomeResponse(rows = listOf(HomeRowDto("trending:movie", "trending", "Trending now", listOf(item("movie_1"))))))
            whatsNew = Result.success(page("movie_5", total = 1234))
        }
        val vm = DiscoverViewModel(repo, FakeUserRepository(), initialType = "movie")
        vm.onScreenShown(); advanceUntilIdle()
        assertEquals("movie", vm.state.value.type)
        assertEquals(1234, vm.state.value.totalTitles)
        assertEquals("movie", repo.browseCalls.first().first.type)
        assertEquals("Trending now", vm.state.value.rows.first().title)
    }

    @Test
    fun `without Home rows from the server, the recommendations row is used`() = runTest(dispatcher) {
        val repo = FakeDiscoverRepository().apply {
            home = Result.failure(ApiError.Http(404, "Not Found"))
            recommendations = Result.success(RecommendationsResponse(data = listOf(item("movie_9")), basedOnTitle = "Heat"))
        }
        val vm = DiscoverViewModel(repo, FakeUserRepository())
        vm.onScreenShown(); advanceUntilIdle()
        assertEquals("recommended", vm.state.value.rows.first().key)
        assertEquals(listOf<String?>(null), repo.recommendationTypes)
    }

    @Test
    fun `empty recommendations are hidden and a missing basedOnTitle gets the generic title`() = runTest(dispatcher) {
        val repo = FakeDiscoverRepository().apply { recommendations = Result.success(RecommendationsResponse(data = emptyList())) }
        val vm = DiscoverViewModel(repo, FakeUserRepository())
        vm.onScreenShown(); advanceUntilIdle()
        assertTrue(vm.state.value.visibleRows.none { it.kind == RowKind.Recommended })

        assertEquals(
            "Recommended for you",
            DiscoverViewModel(FakeDiscoverRepository().apply {
                recommendations = Result.success(RecommendationsResponse(data = listOf(item("movie_1"))))
            }, FakeUserRepository()).also { it.onScreenShown(); advanceUntilIdle() }.state.value.rows.first().title,
        )
    }

    @Test
    fun `nothing at all shows the enrichment hint, cold cache shows the sync message`() = runTest(dispatcher) {
        val vm = DiscoverViewModel(FakeDiscoverRepository(), FakeUserRepository())
        vm.onScreenShown(); advanceUntilIdle()
        assertEquals(DiscoverViewModel.EMPTY_HINT, vm.state.value.emptyHint)

        val cold = FakeDiscoverRepository().apply {
            recommendations = Result.failure(ApiError.ColdCache())
            whatsNew = Result.failure(ApiError.ColdCache())
            facets = Result.failure(ApiError.ColdCache())
        }
        val vm2 = DiscoverViewModel(cold, FakeUserRepository())
        vm2.onScreenShown(); advanceUntilIdle()
        assertEquals(DiscoverViewModel.COLD_CACHE_MESSAGE, vm2.state.value.error)
    }

    @Test
    fun `at most two genre rows fetch at once and only ten genres become rows`() = runTest(dispatcher) {
        val genres = (1..12).map { FacetValue("G$it", 100 - it) }
        val repo = FakeDiscoverRepository().apply {
            facets = Result.success(DiscoverFacets(genres = genres))
            gateGenres = true
        }
        val vm = DiscoverViewModel(repo, FakeUserRepository())
        vm.onScreenShown(); advanceUntilIdle()
        val genreRows = vm.state.value.rows.filter { it.kind == RowKind.Genre }
        assertEquals((1..10).map { "G$it" }, genreRows.map { it.genre })

        genreRows.forEach { vm.onRowVisible(it.key) }
        genreRows.forEach { vm.onRowVisible(it.key) } // repeat calls never refetch
        runCurrent()
        assertEquals(2, repo.inFlight)

        repeat(10) {
            repo.releaseOne()
            runCurrent()
            assertTrue("in flight ${repo.inFlight}", repo.inFlight <= 2)
        }
        advanceUntilIdle()
        assertEquals(2, repo.maxInFlight)
        assertEquals(10, repo.genreFetches)
        assertTrue(vm.state.value.rows.filter { it.kind == RowKind.Genre }.all { it.loaded })
        vm.viewModelScope.cancel()
    }

    @Test
    fun `genre row pages when focus nears its end`() = runTest(dispatcher) {
        val repo = FakeDiscoverRepository().apply {
            facets = Result.success(DiscoverFacets(genres = listOf(FacetValue("Drama", 5))))
            genrePages["Drama"] = Result.success(page("movie_1", "movie_2", total = 4))
        }
        val vm = DiscoverViewModel(repo, FakeUserRepository())
        vm.onScreenShown(); advanceUntilIdle()
        vm.onRowVisible("genre:Drama"); advanceUntilIdle()
        repo.genrePages["Drama"] = Result.success(page("movie_3", "movie_4", total = 4))
        vm.onRowNearEnd("genre:Drama"); advanceUntilIdle()
        val row = vm.state.value.rows.single { it.key == "genre:Drama" }
        assertEquals(listOf("movie_1", "movie_2", "movie_3", "movie_4"), row.items.map { it.id })
        assertEquals(2, row.page)
        assertFalse(row.hasMore)
        assertEquals(2, repo.browseCalls.last().second)
    }

    @Test
    fun `continue watching maps movies and series to detail items`() {
        val movie = DiscoverViewModel.toContinueEntry(record("m1", ProgressMeta(
            mediaId = "m1", type = "movie", title = "Heat", categoryId = "7", screenshot_uri = "/p.jpg", progressPercent = 42, catalogId = "movie_m1",
        )))!!
        assertFalse(movie.isSeries)
        assertEquals("m1", movie.target.id)
        assertEquals("7", movie.target.categoryId)
        assertEquals("Heat", movie.target.title)
        assertEquals("Heat", movie.target.name)
        assertEquals("/p.jpg", movie.target.screenshotUri)
        assertEquals(42, movie.progressPercent)
        assertNull(movie.subtitle)

        val ep = DiscoverViewModel.toContinueEntry(record("ep55", ProgressMeta(
            mediaId = "ep55", type = "series", is_series = 1, title = "Dark", episodeTitle = "Secrets", series_number = 2,
            categoryId = "12", catalogId = "series_301", screenshot_uri = "/d.jpg", progressPercent = 10,
        )))!!
        assertTrue(ep.isSeries)
        assertEquals("301", ep.target.id)
        assertEquals("301", ep.target.seriesId)
        assertEquals(true, ep.target.isSeries)
        assertEquals("12", ep.target.categoryId)
        assertEquals("Dark", ep.target.title)
        assertEquals("S2·Secrets", ep.subtitle)

        // older rows without catalogId fall back to mediaId
        val old = DiscoverViewModel.toContinueEntry(record("s9", ProgressMeta(mediaId = "s9", type = "series", title = "Old")))!!
        assertEquals("s9", old.target.id)
    }

    @Test
    fun `continue watching cards show the wide art and the time left`() {
        val ep = DiscoverViewModel.toContinueEntry(ProgressRecord(
            mediaId = "ep55",
            meta = ProgressMeta(mediaId = "ep55", type = "series", title = "Dark", episodeTitle = "Secrets", series_number = 2,
                currentTime = 1200.0, duration = 2580.0, catalogId = "series_301").toJson(),
            backdropPath = "/api/images/tmdb/original/b.jpg",
        ))!!
        assertEquals("/api/images/tmdb/original/b.jpg", ep.wideImagePath)
        assertEquals("S2·Secrets · 23 min left", ep.detail)
        val movie = DiscoverViewModel.toContinueEntry(record("m1", ProgressMeta(mediaId = "m1", type = "movie", title = "Heat", currentTime = 600.0, duration = 10_320.0)))!!
        assertEquals("2h 42m left", movie.detail)
        assertNull(movie.wideImagePath)
        val noDuration = DiscoverViewModel.toContinueEntry(record("m2", ProgressMeta(mediaId = "m2", type = "movie", title = "Ran")))!!
        assertNull(noDuration.detail)
        assertNull(DiscoverViewModel.timeLeft(30.0))
    }

    @Test
    fun `clicking continue watching navigates without resolving`() = runTest(dispatcher) {
        val user = FakeUserRepository().apply {
            rows = listOf(record("ep55", ProgressMeta(mediaId = "ep55", type = "series", title = "Dark", catalogId = "series_301")))
        }
        val repo = FakeDiscoverRepository()
        val vm = DiscoverViewModel(repo, user)
        vm.onScreenShown(); advanceUntilIdle()
        val entry = vm.state.value.continueWatching.single()
        vm.openContinueWatching(entry)
        assertEquals(OpenRequest(entry.target, isSeries = true), vm.state.value.navigation)
        assertEquals(0, repo.openCalls.size)
        vm.onNavigationHandled()
        assertNull(vm.state.value.navigation)
    }

    @Test
    fun `one variant opens directly`() = runTest(dispatcher) {
        val repo = FakeDiscoverRepository().apply { variants = listOf(item("movie_5").copy(variantLabel = "1080p")) }
        val vm = DiscoverViewModel(repo, FakeUserRepository())
        val target = item("movie_5", "Alien")
        vm.open(target)
        assertTrue(vm.state.value.resolving)
        advanceUntilIdle()
        assertNull(vm.state.value.variants)
        assertEquals(listOf(target), repo.openCalls)
        val nav = vm.state.value.navigation!!
        assertFalse(nav.isSeries)
        assertEquals("5", nav.item.id)
        assertFalse(vm.state.value.resolving)
    }

    @Test
    fun `several variants show the picker and the choice is opened`() = runTest(dispatcher) {
        val v1 = item("series_5").copy(variantLabel = "EN")
        val v2 = item("series_6").copy(variantLabel = "DE")
        val repo = FakeDiscoverRepository().apply { variants = listOf(v1, v2) }
        val vm = DiscoverViewModel(repo, FakeUserRepository())
        vm.open(item("series_5"))
        advanceUntilIdle()
        assertEquals(listOf(v1, v2), vm.state.value.variants)
        assertNull(vm.state.value.navigation)
        assertTrue(repo.openCalls.isEmpty())

        vm.chooseVariant(v2)
        advanceUntilIdle()
        assertNull(vm.state.value.variants)
        assertEquals(listOf(v2), repo.openCalls)
        assertTrue(vm.state.value.navigation!!.isSeries)
        assertEquals("6", vm.state.value.navigation!!.item.id)
    }

    @Test
    fun `open failure surfaces an error`() = runTest(dispatcher) {
        val repo = FakeDiscoverRepository().apply { openResult = { Result.failure(ApiError.Malformed("Movie 5 not found")) } }
        val vm = DiscoverViewModel(repo, FakeUserRepository())
        vm.open(item("movie_5", "Alien"))
        advanceUntilIdle()
        assertNull(vm.state.value.navigation)
        assertTrue(vm.state.value.openError!!.contains("Alien"))
        assertFalse(vm.state.value.resolving)
    }

    @Test
    fun `type toggle reloads rows with the type and filters continue watching`() = runTest(dispatcher) {
        val user = FakeUserRepository().apply {
            rows = listOf(
                record("m1", ProgressMeta(mediaId = "m1", type = "movie", title = "Heat", timestamp = 2)),
                record("e1", ProgressMeta(mediaId = "e1", type = "series", title = "Dark", catalogId = "series_3", timestamp = 1)),
            )
        }
        val repo = FakeDiscoverRepository()
        val vm = DiscoverViewModel(repo, user)
        vm.onScreenShown(); advanceUntilIdle()
        assertEquals(2, vm.state.value.continueWatching.size)
        vm.setType("series"); advanceUntilIdle()
        assertEquals(listOf("Dark"), vm.state.value.continueWatching.map { it.title })
        assertEquals("series", repo.browseCalls.last().first.type)
        assertEquals("series", repo.recommendationTypes.last())
    }

    @Test
    fun `filters switch to a paged grid and clear returns to rows`() = runTest(dispatcher) {
        val repo = FakeDiscoverRepository().apply { filterPages = mutableListOf(page("movie_1", total = 2), page("movie_2", total = 2)) }
        val vm = DiscoverViewModel(repo, FakeUserRepository())
        vm.applyFilter(DiscoverQuery(genres = listOf("Drama", "Crime"), country = "France"))
        advanceUntilIdle()
        val grid = vm.state.value.grid!!
        assertEquals("Drama,Crime", grid.query.genreParam)
        assertEquals(listOf("movie_1"), grid.items.map { it.id })
        assertTrue(grid.hasMore)
        vm.onGridNearEnd(); advanceUntilIdle()
        assertEquals(listOf("movie_1", "movie_2"), vm.state.value.grid!!.items.map { it.id })
        assertFalse(vm.state.value.grid!!.hasMore)
        vm.clearFilter()
        assertNull(vm.state.value.grid)
    }
}

/** Scripted Discover repository. Genre browse calls can be gated to observe concurrency. */
private class FakeDiscoverRepository : DiscoverRepository {
    var facets: Result<DiscoverFacets> = Result.success(DiscoverFacets())
    var recommendations: Result<RecommendationsResponse> = Result.success(RecommendationsResponse())
    /** Null: the server has no Home rows (an older server), as the interface's default. */
    var home: Result<HomeResponse>? = null
    var whatsNew: Result<DiscoverBrowseResponse> = Result.success(DiscoverBrowseResponse())
    val genrePages = mutableMapOf<String, Result<DiscoverBrowseResponse>>()
    var filterPages = mutableListOf<DiscoverBrowseResponse>()
    var variants: List<MediaItem> = emptyList()
    var openResult: (MediaItem) -> Result<MediaItem> = { Result.success(it.copy(id = it.rawId)) }

    val browseCalls = mutableListOf<Pair<DiscoverQuery, Int>>()
    val recommendationTypes = mutableListOf<String?>()
    val openCalls = mutableListOf<MediaItem>()

    var gateGenres = false
    private val gates = ArrayDeque<CompletableDeferred<Unit>>()
    var inFlight = 0
    var maxInFlight = 0
    var genreFetches = 0

    fun releaseOne() { gates.removeFirstOrNull()?.complete(Unit) }

    override suspend fun facets(type: String?) = facets
    override suspend fun home(type: String?): Result<HomeResponse> = home ?: Result.failure(UnsupportedOperationException("home"))
    override suspend fun recommendations(type: String?): Result<RecommendationsResponse> {
        recommendationTypes += type
        return recommendations
    }

    override suspend fun browse(query: DiscoverQuery, page: Int): Result<DiscoverBrowseResponse> {
        browseCalls += query to page
        val genre = query.genres.singleOrNull()
        return when {
            query.genres.isEmpty() && query.country == null && query.language == null -> whatsNew
            genre != null && query.country == null -> {
                genreFetches++
                inFlight++
                maxInFlight = maxOf(maxInFlight, inFlight)
                try {
                    if (gateGenres) CompletableDeferred<Unit>().also { gates.addLast(it) }.await()
                } finally {
                    inFlight--
                }
                genrePages[genre] ?: Result.success(DiscoverBrowseResponse(data = listOf(MediaItem(id = "movie_$genre")), totalItems = 1))
            }
            else -> Result.success(filterPages.removeFirst())
        }
    }

    override suspend fun variants(prefixedId: String) = Result.success(variants)
    override suspend fun openItem(item: MediaItem): Result<MediaItem> {
        openCalls += item
        return openResult(item)
    }
}

private class FakeUserRepository : UserRepository {
    var rows: List<ProgressRecord> = emptyList()
    override suspend fun providerKey() = Result.success("stalker_test")
    override suspend fun preferences(forceRefresh: Boolean) = Result.success(Preferences())
    override suspend fun updatePreferences(patch: Preferences) = Result.success(patch)
    override suspend fun progress(forceRefresh: Boolean) = Result.success(rows)
    override suspend fun saveProgress(record: ProgressRecord) = Result.success(Unit)
    override suspend fun deleteProgress(mediaId: String) = Result.success(Unit)
    override suspend fun continueWatching() = Result.success(rows)
}
