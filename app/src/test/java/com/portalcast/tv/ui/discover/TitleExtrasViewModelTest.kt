package com.portalcast.tv.ui.discover

import com.portalcast.tv.data.model.DiscoverBrowseResponse
import com.portalcast.tv.data.model.DiscoverFacets
import com.portalcast.tv.data.model.DiscoverQuery
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.RecommendationsResponse
import com.portalcast.tv.data.repo.DiscoverRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
class TitleExtrasViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Repo : DiscoverRepository {
        var ids: Result<Set<String>> = Result.success(emptySet())
        var similar: List<MediaItem> = emptyList()
        var saveResult: Result<Unit> = Result.success(Unit)
        var variants: List<MediaItem> = emptyList()
        val saves = mutableListOf<Pair<String, Boolean>>()
        override suspend fun facets(type: String?) = Result.success(DiscoverFacets())
        override suspend fun browse(query: DiscoverQuery, page: Int) = Result.success(DiscoverBrowseResponse())
        override suspend fun recommendations(type: String?) = Result.success(RecommendationsResponse())
        override suspend fun variants(prefixedId: String) = Result.success(variants)
        override suspend fun openItem(item: MediaItem) = Result.success(item.copy(id = item.rawId))
        override suspend fun myListIds() = ids
        override suspend fun similar(contentId: String) = Result.success(similar)
        override suspend fun setInList(contentId: String, inList: Boolean): Result<Unit> { saves += contentId to inList; return saveResult }
    }

    private fun item(id: String, type: String = "movie") = MediaItem(id = id, title = id, type = type)

    @Test
    fun `the button shows the list state and the title itself is left out of More Like This`() = runTest(dispatcher) {
        val repo = Repo().apply { ids = Result.success(setOf("movie_1")); similar = listOf(item("movie_1"), item("movie_2")) }
        val vm = TitleExtrasViewModel("movie_1", repo)
        advanceUntilIdle()
        assertEquals(true, vm.state.value.inList)
        assertEquals(listOf("movie_2"), vm.state.value.similar.map { it.id })
    }

    @Test
    fun `no My List on the server hides the button`() = runTest(dispatcher) {
        val vm = TitleExtrasViewModel("movie_1", Repo().apply { ids = Result.failure(RuntimeException("404")) })
        advanceUntilIdle()
        assertNull(vm.state.value.inList)
        vm.toggleList()
        advanceUntilIdle()
        assertNull(vm.state.value.inList)
    }

    @Test
    fun `toggling flips at once and saves`() = runTest(dispatcher) {
        val repo = Repo()
        val vm = TitleExtrasViewModel("series_7", repo)
        advanceUntilIdle()
        vm.toggleList()
        assertEquals(true, vm.state.value.inList)
        vm.toggleList() // ignored while saving
        advanceUntilIdle()
        assertEquals(listOf("series_7" to true), repo.saves)
        assertEquals(true, vm.state.value.inList)
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun `a refused save puts the button back`() = runTest(dispatcher) {
        val repo = Repo().apply { ids = Result.success(setOf("movie_3")); saveResult = Result.failure(RuntimeException("500")) }
        val vm = TitleExtrasViewModel("movie_3", repo)
        advanceUntilIdle()
        vm.toggleList()
        advanceUntilIdle()
        assertEquals(true, vm.state.value.inList)
        assertTrue(vm.state.value.listError != null)
    }

    @Test
    fun `opening a similar title offers its versions, then resolves the chosen one`() = runTest(dispatcher) {
        val repo = Repo().apply { variants = listOf(item("movie_5"), item("movie_6")) }
        val vm = TitleExtrasViewModel("movie_1", repo)
        advanceUntilIdle()
        vm.opener.open(item("movie_5"))
        advanceUntilIdle()
        assertEquals(2, vm.opener.state.value.variants?.size)
        vm.opener.chooseVariant(item("series_6", "series"))
        advanceUntilIdle()
        val nav = vm.opener.state.value.navigation
        assertEquals("6", nav?.first?.id)
        assertEquals(true, nav?.second)
    }
}
