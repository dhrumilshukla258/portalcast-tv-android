package com.portalcast.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.Paged
import com.portalcast.tv.data.model.Preferences
import com.portalcast.tv.data.repo.LiveRepository
import com.portalcast.tv.data.repo.SeriesRepository
import com.portalcast.tv.data.repo.UserRepository
import com.portalcast.tv.data.repo.VodRepository
import com.portalcast.tv.playback.PlaybackHolder
import com.portalcast.tv.playback.PlaybackSession
import com.portalcast.tv.util.TitleKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Global search: one query across ALL categories of movies and series (server-side,
 * `category=*&search=`, served from the server's cache so it never hits the portal) plus
 * live channels (client-side over the session's cached channel list).
 *
 * Movies/series are grouped like Discover: every language/quality version of a title
 * ("ABC Hindi", "ABC Tamil 4K") collapses into ONE result (same key as the server's
 * Discover grouping, see [TitleKey]); opening a multi-version result asks which version.
 */
class SearchViewModel(
    private val vod: VodRepository,
    private val series: SeriesRepository,
    private val live: LiveRepository,
    private val users: UserRepository,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    enum class Filter(val label: String) { ALL("All"), MOVIES("Movies"), SERIES("Series"), LIVE("Live TV") }

    /** One title and all of its versions, in first-seen order. */
    data class TitleGroup(val key: String, val title: String, val variants: List<MediaItem>) {
        /** What the cards show: the first version, titled with the clean name. */
        val display: MediaItem get() = variants.first().copy(title = title, name = title)
        val versionsLabel: String? get() = if (variants.size > 1) "${variants.size} versions" else variants.first().displayYear
    }

    data class Section(
        val groups: List<TitleGroup> = emptyList(),
        val rawLoaded: Int = 0,
        val total: Int = 0,
        val nextPage: Int = 2,
        val loadingMore: Boolean = false,
    ) {
        val hasMore: Boolean get() = rawLoaded < total
    }

    /** Opening a title: navigate, or ask which version first. */
    sealed class Open {
        data class Movie(val item: MediaItem) : Open()
        data class Series(val item: MediaItem) : Open()
    }

    data class State(
        val query: String = "",
        val filter: Filter = Filter.ALL,
        val loading: Boolean = false,
        val searched: String? = null,
        val movies: Section = Section(),
        val series: Section = Section(),
        val channels: List<Channel> = emptyList(),
        /** Guide for channel results (sorted per channel), loaded lazily; empty if unavailable. */
        val epgIndex: Map<String, List<com.portalcast.tv.data.model.EpgProgramme>> = emptyMap(),
        val error: Throwable? = null,
        val recent: List<String> = emptyList(),
        /** Non-null while the version picker is open. */
        val picker: Pair<TitleGroup, Boolean>? = null,
        /** One-shot navigation request for the screen. */
        val open: Open? = null,
    ) {
        val isEmptyResult: Boolean
            get() = searched != null && !loading && movies.groups.isEmpty() && series.groups.isEmpty() && channels.isEmpty()
    }

    /** Last focused result card key, so Back from a detail/player lands on it again. */
    var lastFocusedKey: String? = null

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            users.preferences().getOrNull()?.recentSearches?.let { r -> _state.update { it.copy(recent = r) } }
        }
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.trim().length < MIN_CHARS) {
            _state.update { it.copy(loading = false, searched = null, movies = Section(), series = Section(), channels = emptyList(), error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            run(q.trim())
        }
    }

    /** IME "Search" or a recent-search chip: search now and remember the query. */
    fun submit(q: String = _state.value.query) {
        val query = q.trim()
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        if (query.length < MIN_CHARS) return
        searchJob = viewModelScope.launch { run(query) }
        rememberQuery(query)
    }

    fun setFilter(f: Filter) = _state.update { it.copy(filter = f) }

    /** A grouped result was chosen: one version opens directly, several open the picker. */
    fun openGroup(group: TitleGroup, isSeries: Boolean) {
        rememberQuery(_state.value.query.trim())
        if (group.variants.size == 1) {
            val item = group.variants.first()
            _state.update { it.copy(open = if (isSeries) Open.Series(item) else Open.Movie(item)) }
        } else {
            _state.update { it.copy(picker = group to isSeries) }
        }
    }

    fun chooseVariant(item: MediaItem) {
        val (_, isSeries) = _state.value.picker ?: return
        _state.update { it.copy(picker = null, open = if (isSeries) Open.Series(item) else Open.Movie(item)) }
    }

    fun dismissPicker() = _state.update { it.copy(picker = null) }
    fun onOpenHandled() = _state.update { it.copy(open = null) }

    /** Live result: hand the matching channels to the player (channel up/down walks the results). */
    fun playChannel(channel: Channel) {
        PlaybackHolder.start(PlaybackSession.Live(channel, _state.value.channels))
        rememberQuery(_state.value.query.trim())
        viewModelScope.launch { live.recordRecent(channel.id, channel.groupId) }
    }

    fun loadMoreMovies() = loadMore(isMovies = true)
    fun loadMoreSeries() = loadMore(isMovies = false)

    private suspend fun run(query: String) {
        _state.update { it.copy(loading = true, error = null) }
        val (m, s, c) = coroutineScope {
            val m = async { vod.movies(ALL, page = 1, search = query) }
            val s = async { series.series(ALL, page = 1, search = query) }
            val c = async { matchChannels(query) }
            Triple(m.await(), s.await(), c.await())
        }
        if (c.isNotEmpty() && _state.value.epgIndex.isEmpty()) loadEpg()
        if (_state.value.query.trim() != query) return // superseded while in flight
        val movies = m.getOrNull()?.let { withContext(compute) { sectionOf(it) } } ?: Section()
        val seriesSec = s.getOrNull()?.let { withContext(compute) { sectionOf(it) } } ?: Section()
        _state.update {
            it.copy(
                loading = false,
                searched = query,
                movies = movies,
                series = seriesSec,
                channels = c,
                error = if (m.isFailure && s.isFailure) m.exceptionOrNull() else null,
            )
        }
    }

    private fun loadEpg() {
        viewModelScope.launch {
            val epg = live.epg().getOrNull() ?: return@launch
            val index = withContext(compute) { com.portalcast.tv.ui.live.LiveEpg.index(epg) }
            _state.update { it.copy(epgIndex = index) }
        }
    }

    private suspend fun matchChannels(query: String): List<Channel> {
        val all = live.channels().getOrNull() ?: return emptyList()
        return withContext(compute) { filterChannels(all, query) }
    }

    private fun loadMore(isMovies: Boolean) {
        val st = _state.value
        val query = st.searched ?: return
        val section = if (isMovies) st.movies else st.series
        if (section.loadingMore || !section.hasMore) return
        update(isMovies) { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val page = section.nextPage
            val result = if (isMovies) vod.movies(ALL, page, search = query) else series.series(ALL, page, search = query)
            if (_state.value.searched != query) return@launch
            val p = result.getOrNull()
            if (p == null) { update(isMovies) { it.copy(loadingMore = false) }; return@launch }
            val merged = withContext(compute) { group(section.groups, p.data) }
            update(isMovies) {
                it.copy(
                    groups = merged,
                    rawLoaded = it.rawLoaded + p.data.size,
                    total = if (p.data.isEmpty()) it.rawLoaded else (p.totalItems ?: it.total),
                    nextPage = page + 1,
                    loadingMore = false,
                )
            }
        }
    }

    private fun sectionOf(p: Paged<MediaItem>) =
        Section(groups = group(emptyList(), p.data), rawLoaded = p.data.size, total = p.totalItems ?: p.data.size)

    private fun update(isMovies: Boolean, f: (Section) -> Section) =
        _state.update { if (isMovies) it.copy(movies = f(it.movies)) else it.copy(series = f(it.series)) }

    private fun rememberQuery(query: String) {
        if (query.length < MIN_CHARS) return
        val next = pushRecent(_state.value.recent, query)
        if (next == _state.value.recent) return
        _state.update { it.copy(recent = next) }
        viewModelScope.launch { users.updatePreferences(Preferences(recentSearches = next)) }
    }

    companion object {
        const val MIN_CHARS = 2
        const val DEBOUNCE_MS = 400L
        const val MAX_CHANNELS = 60
        const val MAX_RECENT = 6
        private const val ALL = "*"

        /**
         * Adds [items] to [existing] groups (later pages can add versions to a title already
         * shown). O(n): one map lookup per item, order of first appearance preserved.
         */
        fun group(existing: List<TitleGroup>, items: List<MediaItem>): List<TitleGroup> {
            val byKey = LinkedHashMap<String, TitleGroup>(existing.size + items.size)
            for (g in existing) byKey[g.key] = g
            for (item in items) {
                val raw = item.displayTitle
                val key = TitleKey.normalize(raw)
                val g = byKey[key]
                if (g == null) {
                    byKey[key] = TitleGroup(key, TitleKey.stripReleaseNoise(raw).ifBlank { raw }, listOf(item))
                } else if (g.variants.none { it.id == item.id }) {
                    byKey[key] = g.copy(variants = g.variants + item)
                }
            }
            return byKey.values.toList()
        }

        /** Case-insensitive name match, or an exact channel number; at most [MAX_CHANNELS]. */
        fun filterChannels(all: List<Channel>, query: String): List<Channel> {
            val q = query.trim()
            if (q.isEmpty()) return emptyList()
            val number = q.toIntOrNull()
            val out = ArrayList<Channel>()
            for (c in all) {
                if ((number != null && c.number == number) || c.displayName.contains(q, ignoreCase = true)) {
                    out.add(c)
                    if (out.size >= MAX_CHANNELS) break
                }
            }
            return out
        }

        /** Newest first, case-insensitive de-duplication, capped (matches the webui). */
        fun pushRecent(recent: List<String>, query: String): List<String> =
            (listOf(query) + recent.filterNot { it.equals(query, ignoreCase = true) }).take(MAX_RECENT)
    }
}
