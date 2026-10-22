package com.portalcast.tv.ui.discover

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.portalcast.tv.data.model.DiscoverFacets
import com.portalcast.tv.data.model.DiscoverQuery
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.ProgressRecord
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.repo.DiscoverRepository
import com.portalcast.tv.data.repo.UserRepository
import com.portalcast.tv.ui.components.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class RowKind { Recommended, WhatsNew, Genre }

/** One horizontal Discover row. Genre rows start unrequested and load when they near the viewport. */
@Immutable // lets unchanged rows skip recomposition when another row loads
data class DiscoverRow(
    val key: String,
    val kind: RowKind,
    val title: String,
    val genre: String? = null,
    val items: List<MediaItem> = emptyList(),
    /** Last page loaded; 0 = nothing loaded yet. */
    val page: Int = 0,
    val hasMore: Boolean = false,
    val requested: Boolean = false,
    val loadingMore: Boolean = false,
    val failed: Boolean = false,
) {
    val loaded: Boolean get() = page > 0

    /**
     * Pending genre rows stay in the list (as placeholders) so they can scroll into view
     * and trigger their fetch; any row known to be empty, or failed, is hidden.
     */
    val visible: Boolean
        get() = when {
            failed -> false
            kind == RowKind.Genre && !loaded -> true
            else -> items.isNotEmpty()
        }
}

/** A Continue Watching card plus the list item its click hands to the detail screen. */
@Immutable
data class ContinueEntry(
    val key: String,
    val title: String,
    val subtitle: String?,
    val imagePath: String?,
    val progressPercent: Int,
    val isSeries: Boolean,
    val target: MediaItem,
    /** Wide art for the 16:9 card: the title's backdrop, else null (the poster is used). */
    val wideImagePath: String? = null,
    /** "S1 · Pilot · 23 min left", "1h 12m left". */
    val detail: String? = null,
)

/** Filtered grid mode: `discover/browse` with the overlay's query, paged 40 at a time. */
data class FilterGrid(
    val query: DiscoverQuery,
    val items: List<MediaItem> = emptyList(),
    val page: Int = 0,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

data class OpenRequest(val item: MediaItem, val isSeries: Boolean)

data class DiscoverUiState(
    /** null = All, else "movie" | "series". */
    val type: String? = null,
    /** How many titles of this type the catalog has (What's new's total), for the header. */
    val totalTitles: Int? = null,
    val loading: Boolean = true,
    val continueWatching: List<ContinueEntry> = emptyList(),
    /** Recommended, What's new, then genre rows, in display order (hidden ones included). */
    val rows: List<DiscoverRow> = emptyList(),
    val facets: DiscoverFacets = DiscoverFacets(),
    val error: String? = null,
    val emptyHint: String? = null,
    val grid: FilterGrid? = null,
    /** A Discover item is being resolved (variants / open-item). */
    val resolving: Boolean = false,
    /** Non-null while the variant picker is showing. */
    val variants: List<MediaItem>? = null,
    val openError: String? = null,
    /** Set when an item is ready for a detail screen; the screen calls [DiscoverViewModel.onNavigationHandled]. */
    val navigation: OpenRequest? = null,
) {
    val visibleRows: List<DiscoverRow> get() = rows.filter { it.visible }
}

/**
 * Discover tab state (architecture plan §3): Continue Watching, "Because you watched",
 * What's new, then up to [MAX_GENRE_ROWS] genre rows fetched lazily through a
 * [Semaphore] of [MAX_ROW_FETCHES], plus a filtered grid mode and the
 * variants → open-item resolve flow.
 */
class DiscoverViewModel(
    private val discover: DiscoverRepository,
    private val user: UserRepository,
    /** The Movies / Series tabs: this screen shows one type only. */
    initialType: String? = null,
) : ViewModel() {

    /** Last focused card ("cw|key" or "rowKey|itemId"), so Back from a detail lands on it again. */
    var lastFocusedKey: String? = null

    private val _state = MutableStateFlow(DiscoverUiState(type = initialType))
    val state: StateFlow<DiscoverUiState> = _state

    /** Bounds genre-row fetches (first page and paging alike). */
    private val rowPermits = Semaphore(MAX_ROW_FETCHES)

    private var started = false
    private var generation = 0
    private var rowsJob: Job? = null
    private var gridJob: Job? = null
    private var openJob: Job? = null

    init {
        // Watched / Not interested / My List from the title menu: Home's rows change.
        viewModelScope.launch { com.portalcast.tv.ui.components.TitleChanges.events.collect { if (started) reload() } }
    }

    /** Screen entered composition: first time loads everything, later only refreshes Continue Watching. */
    fun onScreenShown() {
        if (!started) {
            started = true
            reload()
        } else {
            rowsScope().launch { loadContinueWatching() }
        }
    }

    fun setType(type: String?) {
        if (type == _state.value.type) return
        _state.update { it.copy(type = type) }
        reload()
        _state.value.grid?.let { applyFilter(it.query.copy(type = type)) }
    }

    fun retry() = reload()

    private fun rowsScope(): CoroutineScope {
        val job = rowsJob?.takeIf { it.isActive } ?: SupervisorJob(viewModelScope.coroutineContext[Job]).also { rowsJob = it }
        return CoroutineScope(viewModelScope.coroutineContext + job)
    }

    private fun reload() {
        rowsJob?.cancel()
        rowsJob = null
        val gen = ++generation
        val type = _state.value.type
        _state.update { it.copy(loading = true, error = null, emptyHint = null, rows = emptyList()) }
        val scope = rowsScope()
        scope.launch { loadContinueWatching() }
        scope.launch {
            // The server's prepared rows (GET /api/v2/home); an older server has none, and the
            // single recommendations row is used instead.
            val homeD = async { discover.home(type) }
            val newD = async { discover.browse(DiscoverQuery(type = type), 1) }
            val facD = async { discover.facets(type) }
            val home = homeD.await()
            val rec = if (home.isSuccess) null else discover.recommendations(type)
            val new = newD.await()
            val fac = facD.await()
            if (gen != generation) return@launch

            val rows = buildList {
                home.getOrNull()?.rows?.forEach { r ->
                    add(DiscoverRow(homeKey(r.key), RowKind.Recommended, r.title, items = r.data, page = 1, requested = true))
                }
                rec?.getOrNull()?.let { r ->
                    val title = r.basedOnTitle?.takeIf { it.isNotBlank() }?.let { "Because you watched $it" } ?: "Recommended for you"
                    add(DiscoverRow(KEY_RECOMMENDED, RowKind.Recommended, title, items = r.data, page = 1, requested = true))
                }
                new.getOrNull()?.let { b ->
                    add(DiscoverRow(KEY_WHATS_NEW, RowKind.WhatsNew, "What's new", items = b.data, page = 1,
                        hasMore = b.hasMore(b.data.size), requested = true))
                    _state.update { s -> s.copy(totalTitles = b.totalItems) }
                }
                fac.getOrNull()?.let { f ->
                    genresFor(f, home.getOrNull()?.genreOrder.orEmpty()).forEach { add(DiscoverRow(genreKey(it), RowKind.Genre, it, genre = it)) }
                }
            }
            val failures = listOf(rec ?: home, new, fac).mapNotNull { it.exceptionOrNull() }
            val error = if (failures.size == 3) discoverMessage(failures.first()) else null
            val nothing = error == null && rows.none { it.kind == RowKind.Genre || it.items.isNotEmpty() }
            _state.update {
                it.copy(
                    loading = false,
                    rows = rows,
                    facets = fac.getOrNull() ?: it.facets,
                    error = error,
                    emptyHint = if (nothing) EMPTY_HINT else null,
                )
            }
        }
    }

    private suspend fun loadContinueWatching() {
        val type = _state.value.type
        val entries = user.continueWatching().getOrNull().orEmpty()
            .mapNotNull { toContinueEntry(it) }
            .filter { type == null || (if (type == "series") it.isSeries else !it.isSeries) }
        _state.update { it.copy(continueWatching = entries) }
    }

    /**
     * Long-press > Remove on a Continue Watching card: the card goes at once, the progress
     * row is deleted on the server; on failure the row list is reloaded so the card returns.
     */
    fun removeContinue(entry: ContinueEntry) {
        _state.update { st -> st.copy(continueWatching = st.continueWatching.filterNot { it.key == entry.key }) }
        viewModelScope.launch {
            if (user.deleteProgress(entry.key).isFailure) loadContinueWatching()
        }
    }

    /** A row (genre rows only do anything) came near the viewport: fetch its first page once. */
    fun onRowVisible(key: String) {
        val row = _state.value.rows.firstOrNull { it.key == key } ?: return
        if (row.kind != RowKind.Genre || row.requested) return
        updateRow(key) { it.copy(requested = true) }
        val gen = generation
        val query = DiscoverQuery(type = _state.value.type, genres = listOfNotNull(row.genre))
        rowsScope().launch {
            val result = rowPermits.withPermit { discover.browse(query, 1) }
            if (gen != generation) return@launch
            result.fold(
                { b -> updateRow(key) { it.copy(items = b.data, page = 1, hasMore = b.hasMore(b.data.size)) } },
                { updateRow(key) { it.copy(failed = true) } },
            )
        }
    }

    /** Focus (or scrolling) neared the end of a row: fetch its next page. */
    fun onRowNearEnd(key: String) {
        val row = _state.value.rows.firstOrNull { it.key == key } ?: return
        if (row.kind == RowKind.Recommended || !row.loaded || !row.hasMore || row.loadingMore) return
        updateRow(key) { it.copy(loadingMore = true) }
        val gen = generation
        val type = _state.value.type
        val query = DiscoverQuery(type = type, genres = listOfNotNull(row.genre))
        rowsScope().launch {
            val result = rowPermits.withPermit { discover.browse(query, row.page + 1) }
            if (gen != generation) return@launch
            result.fold(
                { b ->
                    updateRow(key) {
                        val merged = mergeById(it.items, b.data)
                        it.copy(items = merged, page = row.page + 1, loadingMore = false,
                            hasMore = b.data.isNotEmpty() && b.hasMore(merged.size))
                    }
                },
                { updateRow(key) { it.copy(loadingMore = false, hasMore = false) } },
            )
        }
    }

    private fun updateRow(key: String, transform: (DiscoverRow) -> DiscoverRow) {
        _state.update { s -> s.copy(rows = s.rows.map { if (it.key == key) transform(it) else it }) }
    }

    // ---- filtered grid ---------------------------------------------------------

    fun applyFilter(query: DiscoverQuery) {
        val q = query.copy(type = _state.value.type)
        if (q.genres.isEmpty() && q.country.isNullOrBlank() && q.language.isNullOrBlank() && q.theme.isNullOrBlank()) {
            clearFilter()
            return
        }
        gridJob?.cancel()
        _state.update { it.copy(grid = FilterGrid(q)) }
        gridJob = viewModelScope.launch {
            discover.browse(q, 1).fold(
                { b -> _state.update { it.copy(grid = FilterGrid(q, b.data, 1, b.totalItems ?: b.data.size, b.hasMore(b.data.size), loading = false)) } },
                { e -> _state.update { it.copy(grid = FilterGrid(q, loading = false, error = discoverMessage(e))) } },
            )
        }
    }

    fun onGridNearEnd() {
        val grid = _state.value.grid ?: return
        if (grid.loading || grid.loadingMore || !grid.hasMore) return
        _state.update { it.copy(grid = grid.copy(loadingMore = true)) }
        gridJob = viewModelScope.launch {
            val result = discover.browse(grid.query, grid.page + 1)
            _state.update { s ->
                val g = s.grid?.takeIf { it.query == grid.query } ?: return@update s
                result.fold(
                    { b ->
                        val merged = mergeById(g.items, b.data)
                        s.copy(grid = g.copy(items = merged, page = g.page + 1, loadingMore = false,
                            hasMore = b.data.isNotEmpty() && b.hasMore(merged.size)))
                    },
                    { s.copy(grid = g.copy(loadingMore = false, hasMore = false)) },
                )
            }
        }
    }

    fun clearFilter() {
        gridJob?.cancel()
        _state.update { it.copy(grid = null) }
    }

    // ---- opening items ---------------------------------------------------------

    /** Discover card clicked: variants first, picker when there is a choice, else resolve and open. */
    fun open(item: MediaItem) {
        if (_state.value.resolving) return
        _state.update { it.copy(resolving = true, openError = null) }
        openJob = viewModelScope.launch {
            val variants = discover.variants(item.id).getOrNull().orEmpty()
            if (variants.size > 1) {
                _state.update { it.copy(resolving = false, variants = variants) }
            } else {
                resolve(item)
            }
        }
    }

    fun chooseVariant(variant: MediaItem) {
        _state.update { it.copy(variants = null, resolving = true, openError = null) }
        openJob = viewModelScope.launch { resolve(variant) }
    }

    fun dismissVariants() = _state.update { it.copy(variants = null) }

    private suspend fun resolve(item: MediaItem) {
        discover.openItem(item).fold(
            { resolved ->
                val isSeries = (resolved.discoverType ?: item.discoverType) == "series"
                _state.update { it.copy(resolving = false, navigation = OpenRequest(resolved, isSeries)) }
            },
            { e -> _state.update { it.copy(resolving = false, openError = "Couldn't open \"${item.displayTitle}\": ${discoverMessage(e)}") } },
        )
    }

    /** Continue Watching card clicked: the detail screen owns Resume, so no resolve step. */
    fun openContinueWatching(entry: ContinueEntry) =
        _state.update { it.copy(navigation = OpenRequest(entry.target, entry.isSeries)) }

    fun onNavigationHandled() = _state.update { it.copy(navigation = null) }

    fun clearOpenError() = _state.update { it.copy(openError = null) }

    companion object {
        const val MAX_ROW_FETCHES = 2
        const val MAX_GENRE_ROWS = 10
        const val KEY_RECOMMENDED = "recommended"
        const val KEY_WHATS_NEW = "whats_new"
        const val COLD_CACHE_MESSAGE = "Your server's library hasn't been synced yet."
        const val EMPTY_HINT = "Nothing to discover yet. Discover needs TMDB enrichment on the server; ask the admin to run it."

        fun genreKey(genre: String) = "genre:$genre"
        fun homeKey(key: String) = "home:$key"

        /**
         * Genre rows: the viewer's genres first (most watched first, from the server), then the
         * biggest genres by count; "Uncategorized" skipped, at most [MAX_GENRE_ROWS].
         */
        fun genresFor(facets: DiscoverFacets, preferred: List<String> = emptyList()): List<String> {
            val byCount = facets.genres
                .filter { it.value.isNotBlank() && !it.value.equals("Uncategorized", ignoreCase = true) }
                .sortedByDescending { it.count }
                .map { it.value }
                .distinct()
            val known = byCount.toSet()
            return (preferred.filter { it in known } + byCount).distinct().take(MAX_GENRE_ROWS)
        }

        fun discoverMessage(e: Throwable): String = if (e is ApiError.ColdCache) COLD_CACHE_MESSAGE else e.userMessage()

        private fun mergeById(existing: List<MediaItem>, more: List<MediaItem>): List<MediaItem> {
            val ids = existing.mapTo(HashSet()) { it.id }
            return existing + more.filter { ids.add(it.id) }
        }

        /**
         * A progress row → card + detail-screen item (webui ContinueWatching.tsx): movies open
         * under `meta.mediaId`; series under the series id from `meta.catalogId` (`series_<id>`),
         * falling back to the row's mediaId for rows saved before catalogId existed.
         */
        fun toContinueEntry(record: ProgressRecord): ContinueEntry? {
            val meta = record.parsedMeta ?: return null
            val isSeries = meta.type == "series" || meta.is_series == 1
            val title = meta.title?.takeIf { it.isNotBlank() } ?: meta.name ?: return null
            val id = if (isSeries) {
                meta.catalogId?.takeIf { it.startsWith("series_") }?.removePrefix("series_")?.takeIf { it.isNotBlank() }
                    ?: meta.mediaId?.takeIf { it.isNotBlank() } ?: record.mediaId
            } else {
                meta.mediaId?.takeIf { it.isNotBlank() } ?: record.mediaId
            }
            val subtitle = if (isSeries) {
                listOfNotNull(meta.series_number?.let { "S$it" }, meta.episodeTitle?.takeIf { it.isNotBlank() })
                    .joinToString("·").takeIf { it.isNotEmpty() }
            } else null
            val target = MediaItem(
                id = id,
                type = if (isSeries) "series" else "movie",
                categoryId = meta.categoryId,
                title = title,
                name = title,
                screenshotUri = meta.screenshot_uri,
                catalogId = meta.catalogId,
                isSeries = if (isSeries) true else null,
                seriesId = if (isSeries) id else null,
            )
            val position = meta.currentTime ?: record.progress
            val left = meta.duration?.let { d -> timeLeft(d - position) }
            return ContinueEntry(
                key = record.mediaId,
                title = title,
                subtitle = subtitle,
                imagePath = meta.screenshot_uri,
                progressPercent = (meta.progressPercent ?: 0).coerceIn(0, 100),
                isSeries = isSeries,
                target = target,
                wideImagePath = record.backdropPath?.takeIf { it.isNotBlank() },
                detail = listOfNotNull(subtitle, left).joinToString(" · ").ifEmpty { null },
            )
        }

        /** "23 min left", "1h 12m left"; null under a minute or when unknown. */
        fun timeLeft(seconds: Double): String? {
            val min = (seconds / 60).toInt()
            if (min < 1) return null
            return if (min < 60) "$min min left" else "${min / 60}h ${min % 60}m left"
        }
    }
}
