package com.portalcast.tv.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.portalcast.tv.data.model.ChannelGroup
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.Paged
import com.portalcast.tv.data.repo.SeriesRepository
import com.portalcast.tv.data.repo.VodRepository
import com.portalcast.tv.ui.components.PagedList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** `sort` values the list routes accept (contract §3.2). */
enum class CatalogSort(val param: String, val label: String) {
    LATEST("latest", "Latest"),
    ALPHABETIC("alphabetic", "A–Z");

    fun toggled(): CatalogSort = if (this == LATEST) ALPHABETIC else LATEST
}

/** Where a catalog grid gets its categories and pages: movies or series. */
interface CatalogSource {
    suspend fun groups(): Result<List<ChannelGroup>>
    suspend fun page(category: String, page: Int, search: String?, sort: String?): Result<Paged<MediaItem>>

    class Movies(private val repo: VodRepository) : CatalogSource {
        override suspend fun groups() = repo.groups()
        override suspend fun page(category: String, page: Int, search: String?, sort: String?) = repo.movies(category, page, search, sort)
    }

    class Series(private val repo: SeriesRepository) : CatalogSource {
        override suspend fun groups() = repo.groups()
        override suspend fun page(category: String, page: Int, search: String?, sort: String?) = repo.series(category, page, search, sort)
    }
}

/**
 * Category rail + infinitely paged poster grid, shared by the Movies and Series tabs.
 * Every reset (category, sort, search) bumps a generation so a late page from the
 * previous query is dropped.
 */
/**
 * @param only show just this category (My library: "local", the NAS files); null: the IPTV
 *   provider's categories, without the library (it has its own filter).
 */
class CatalogViewModel(private val source: CatalogSource, private val only: String? = null) : ViewModel() {

    data class State(
        val groupsLoading: Boolean = true,
        val groupsError: Throwable? = null,
        val groups: List<ChannelGroup> = emptyList(),
        val selectedCategory: String = ChannelGroup.ALL_ID,
        val sort: CatalogSort = CatalogSort.LATEST,
        val query: String = "",
        val list: PagedList<MediaItem> = PagedList(),
        val pageLoading: Boolean = false,
        val pageError: Throwable? = null,
    ) {
        val selectedGroup: ChannelGroup? get() = groups.firstOrNull { it.id == selectedCategory }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** Last focused item id, for focus restoration when coming back from a detail screen. */
    var lastFocusedId: String? = null

    private var generation = 0
    private var pageJob: Job? = null
    private var searchJob: Job? = null

    init { reload() }

    /** Groups and the first page again (Retry). */
    fun reload() {
        _state.update { it.copy(groupsLoading = true, groupsError = null) }
        viewModelScope.launch {
            source.groups().fold(
                onSuccess = { all ->
                    val groups = if (only != null) {
                        all.filter { it.id == only }.ifEmpty { listOf(ChannelGroup.synthetic(only, "My library")) }
                    } else {
                        all.filterNot { it.id == LOCAL_CATEGORY || it.displayTitle.trim().equals("My Library", ignoreCase = true) }
                    }
                    val list = groups.ifEmpty { listOf(ChannelGroup.synthetic(ChannelGroup.ALL_ID, "All")) }
                    _state.update { s ->
                        s.copy(
                            groupsLoading = false,
                            groups = list,
                            selectedCategory = only ?: s.selectedCategory.takeIf { c -> list.any { it.id == c } } ?: ChannelGroup.ALL_ID,
                        )
                    }
                    resetList()
                },
                onFailure = { e -> _state.update { it.copy(groupsLoading = false, groupsError = e) } },
            )
        }
    }

    fun selectCategory(id: String) {
        if (id == _state.value.selectedCategory) return
        _state.update { it.copy(selectedCategory = id) }
        resetList()
    }

    fun toggleSort() {
        _state.update { it.copy(sort = it.sort.toggled()) }
        resetList()
    }

    /** Debounced: typing triggers one query 600 ms after the last key. */
    fun setQuery(q: String) {
        if (q == _state.value.query) return
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(600)
            resetList()
        }
    }

    /** IME action: search now. */
    fun submitSearch() {
        searchJob?.cancel()
        resetList()
    }

    /** Called with the grid's last visible index; fetches the next page when near the end. */
    fun onVisible(lastVisibleIndex: Int) {
        if (_state.value.list.shouldLoadMore(lastVisibleIndex)) loadMore()
    }

    fun loadMore() {
        val s = _state.value
        if (s.pageLoading || s.list.endReached || s.groupsLoading || s.groupsError != null) return
        val gen = generation
        _state.update { it.copy(pageLoading = true, pageError = null) }
        pageJob = viewModelScope.launch {
            val result = source.page(s.selectedCategory, s.list.nextPage, s.query.trim().ifEmpty { null }, s.sort.param)
            if (gen != generation) return@launch
            result.fold(
                onSuccess = { page -> _state.update { it.copy(pageLoading = false, list = it.list.append(page) { item -> itemKey(item) }) } },
                onFailure = { e -> _state.update { it.copy(pageLoading = false, pageError = e) } },
            )
        }
    }

    private fun resetList() {
        generation++
        pageJob?.cancel()
        lastFocusedId = null
        _state.update { it.copy(list = PagedList(), pageLoading = false, pageError = null) }
        loadMore()
    }

    companion object {
        fun itemKey(item: MediaItem): String = item.id.ifBlank { item.displayTitle }
    }
}

/** The catalog category of NAS library titles (server: LOCAL_CATEGORY). */
const val LOCAL_CATEGORY = "local"
