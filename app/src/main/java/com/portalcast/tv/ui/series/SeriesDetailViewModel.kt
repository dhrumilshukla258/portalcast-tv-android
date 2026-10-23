package com.portalcast.tv.ui.series

import kotlinx.coroutines.flow.SharedFlow
import com.portalcast.tv.data.repo.DefaultSeriesRepository
import com.portalcast.tv.data.repo.DiscoverRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.ProgressRecord
import com.portalcast.tv.data.repo.SeriesRepository
import com.portalcast.tv.data.repo.UserRepository
import com.portalcast.tv.playback.PlaybackHolder
import com.portalcast.tv.playback.PlaybackSession
import com.portalcast.tv.ui.SelectedItemHolder
import com.portalcast.tv.ui.components.PagedList
import com.portalcast.tv.ui.components.ProgressRows
import com.portalcast.tv.ui.components.panelMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Series detail: seasons as tabs, the selected season's episodes paged 14 at a time,
 * per-episode progress and a Resume entry from the row saved under the series id.
 * The series item comes from [SelectedItemHolder]; without it a minimal item is built
 * from the route (title then falls back to the progress row's `meta.title`).
 */
class SeriesDetailViewModel(
    private val seriesId: String,
    private val category: String,
    private val repo: SeriesRepository,
    private val users: UserRepository,
    /** For the show's full card when the page was opened from a thin one. */
    private val discover: DiscoverRepository? = null,
    /** Stored answers that changed after being shown (DiskCache.updates): re-read quietly. */
    private val cacheUpdates: SharedFlow<String>? = null,
) : ViewModel() {

    data class State(
        val series: MediaItem,
        val seasonsLoading: Boolean = true,
        val seasonsError: Throwable? = null,
        val seasons: List<MediaItem> = emptyList(),
        val selectedSeasonId: String? = null,
        val episodes: PagedList<MediaItem> = PagedList(),
        val episodesLoading: Boolean = false,
        val episodesError: Throwable? = null,
        val fractions: Map<String, Float> = emptyMap(),
        val continueRow: ProgressRecord? = null,
        /** The loaded episode [continueRow] points at, when its season is the selected one. */
        val continueEpisode: MediaItem? = null,
        val starting: Boolean = false,
        val playError: String? = null,
    ) {
        val selectedSeasonIndex: Int get() = seasons.indexOfFirst { it.id == selectedSeasonId }
    }

    private val held = SelectedItemHolder.get(seriesId)
    private val _state = MutableStateFlow(
        State(series = held ?: MediaItem(id = seriesId, categoryId = category.takeUnless { it.isBlank() || it == "*" })),
    )
    val state: StateFlow<State> = _state

    /** Last focused episode id, for focus restoration when coming back from the player. */
    var lastFocusedEpisodeId: String? = null

    /** Set by the screen on its first showing; later showings (back from the player) refresh progress. */
    var shownOnce = false

    private val effectiveCategory: String?
        get() = category.takeUnless { it.isBlank() || it == "*" } ?: _state.value.series.categoryId?.takeIf { it.isNotBlank() }

    private var episodesGeneration = 0
    private var episodesJob: Job? = null

    /** The viewer picked a season this showing: progress arriving later does not move it. */
    private var seasonPickedByViewer = false

    init {
        // Seasons do not wait for progress (they are often stored already and show at once);
        // progress, when it lands, moves to the season being watched unless the viewer chose one.
        viewModelScope.launch {
            loadProgressNow()
            val wanted = _state.value.continueRow?.parsedMeta?.seasonId
            val s = _state.value
            if (!seasonPickedByViewer && wanted != null && wanted != s.selectedSeasonId && s.seasons.any { it.id == wanted }) {
                selectSeason(wanted, force = true)
            }
        }
        loadSeasons()
        loadFullCard()
        cacheUpdates?.let { flow ->
            viewModelScope.launch { flow.collect { ns -> if (ns == DefaultSeriesRepository.NS) refreshQuietly() } }
        }
    }

    /**
     * A stored answer was shown and the server's differs: read seasons and the loaded episode
     * pages again (from memory now) and swap them in, without clearing the list (focus stays).
     */
    private fun refreshQuietly() {
        viewModelScope.launch {
            val fresh = repo.seasons(seriesId, effectiveCategory).getOrNull() ?: return@launch
            val seasons = EpisodeInfo.sortSeasons(fresh)
            if (seasons != _state.value.seasons) _state.update { it.copy(seasons = seasons) }
            val s = _state.value
            val seasonId = s.selectedSeasonId ?: return@launch
            if (s.episodesLoading) return@launch
            val pages = (s.episodes.nextPage - 1).coerceAtLeast(1)
            var list = PagedList<MediaItem>()
            for (p in 1..pages) {
                val page = repo.episodes(seriesId, seasonId, p, effectiveCategory).getOrNull() ?: return@launch
                list = list.append(page) { ep -> ep.id.ifBlank { ep.displayTitle } }
                if (list.endReached) break
            }
            val sorted = list.copy(items = EpisodeInfo.sortEpisodes(list.items))
            _state.update { st ->
                if (st.selectedSeasonId != seasonId || st.episodes.items == sorted.items) st
                else st.copy(episodes = sorted).withContinueEpisode()
            }
        }
    }

    /**
     * Opened from Continue Watching (or anything that carries only a title and a poster) the
     * page had no overview, year, rating, genres or backdrop (owner, 2026-10-22: "the details of
     * the whole series are not accessible"). The show's Discover card fills what is missing.
     */
    private fun loadFullCard() {
        val s = _state.value.series
        if (s.overview != null && s.year != null && (s.backdropPath != null || s.backdropHdPath != null)) return
        val repo = discover ?: return
        val contentId = s.catalogId?.takeIf { it.startsWith("series_") } ?: if (seriesId.startsWith("series_")) seriesId else "series_${s.rawId.ifBlank { seriesId }}"
        viewModelScope.launch {
            val list = repo.variants(contentId).getOrNull().orEmpty()
            val full = list.firstOrNull { it.id == contentId } ?: list.firstOrNull() ?: return@launch
            _state.update { it.copy(series = it.series.filledFrom(full)) }
        }
    }

    private fun MediaItem.filledFrom(f: MediaItem): MediaItem = copy(
        description = description ?: f.description,
        plot = plot ?: f.plot,
        year = year ?: f.year,
        ratingBest = ratingBest ?: f.ratingBest,
        ratingSource = ratingSource ?: f.ratingSource,
        ratingImdb = ratingImdb ?: f.ratingImdb,
        genresStr = genresStr ?: f.genresStr,
        runtime = runtime ?: f.runtime,
        country = country ?: f.country,
        director = director ?: f.director,
        cast = cast ?: f.cast,
        actors = actors ?: f.actors,
        backdropPath = backdropPath ?: f.backdropPath,
        backdropHdPath = backdropHdPath ?: f.backdropHdPath,
        screenshotUri = screenshotUri ?: f.screenshotUri,
        logoPath = logoPath ?: f.logoPath,
        catalogId = catalogId ?: f.catalogId,
    )

    fun loadSeasons() {
        _state.update { it.copy(seasonsLoading = true, seasonsError = null) }
        viewModelScope.launch {
            repo.seasons(seriesId, effectiveCategory).fold(
                onSuccess = { raw ->
                    // Portals list seasons newest first; show Season 1 first.
                    val seasons = EpisodeInfo.sortSeasons(raw)
                    val wanted = _state.value.continueRow?.parsedMeta?.seasonId
                    val pick = seasons.firstOrNull { it.id == wanted }?.id ?: seasons.firstOrNull()?.id
                    _state.update { it.copy(seasonsLoading = false, seasons = seasons) }
                    if (pick != null) selectSeason(pick, force = true)
                },
                onFailure = { e -> _state.update { it.copy(seasonsLoading = false, seasonsError = e) } },
            )
        }
    }

    fun selectSeason(seasonId: String, force: Boolean = false) {
        if (!force) seasonPickedByViewer = true
        if (!force && seasonId == _state.value.selectedSeasonId) return
        episodesGeneration++
        episodesJob?.cancel()
        _state.update {
            it.copy(selectedSeasonId = seasonId, episodes = PagedList(), episodesLoading = false, episodesError = null, continueEpisode = null)
        }
        loadMoreEpisodes()
    }

    fun onEpisodeVisible(lastVisibleIndex: Int) {
        if (_state.value.episodes.shouldLoadMore(lastVisibleIndex, threshold = 4)) loadMoreEpisodes()
    }

    fun loadMoreEpisodes() {
        val s = _state.value
        val seasonId = s.selectedSeasonId ?: return
        if (s.episodesLoading || s.episodes.endReached) return
        val gen = episodesGeneration
        _state.update { it.copy(episodesLoading = true, episodesError = null) }
        episodesJob = viewModelScope.launch {
            val result = repo.episodes(seriesId, seasonId, s.episodes.nextPage, effectiveCategory)
            if (gen != episodesGeneration) return@launch
            result.fold(
                onSuccess = { page ->
                    _state.update { st ->
                        val appended = st.episodes.append(page) { ep -> ep.id.ifBlank { ep.displayTitle } }
                        // Ascending episode order (portals list newest first); number from the name.
                        val episodes = appended.copy(items = EpisodeInfo.sortEpisodes(appended.items))
                        st.copy(episodesLoading = false, episodes = episodes).withContinueEpisode()
                    }
                    // Load the whole season up front (bounded) so sorting is over all of it and
                    // episode 1 is first; a season is rarely more than a few pages.
                    val now = _state.value.episodes
                    if (!now.endReached && now.nextPage <= MAX_EPISODE_PAGES) loadMoreEpisodes()
                },
                onFailure = { e -> _state.update { it.copy(episodesLoading = false, episodesError = e) } },
            )
        }
    }

    /** Re-read progress rows (on re-entry from the player). */
    fun refreshProgress() {
        viewModelScope.launch { loadProgressNow() }
    }

    private suspend fun loadProgressNow() {
        val rows = users.progress(forceRefresh = true).getOrNull() ?: return
        _state.update { s ->
            val row = SeriesProgress.continueRow(rows, seriesId)
            val series = if (s.series.displayTitle.isBlank()) {
                s.series.copy(title = row?.parsedMeta?.title, screenshotUri = s.series.screenshotUri ?: row?.parsedMeta?.screenshot_uri?.takeIf { it.isNotBlank() })
            } else s.series
            s.copy(series = series, fractions = SeriesProgress.episodeFractions(rows, seriesId), continueRow = row).withContinueEpisode()
        }
    }

    /** Resolve the episode file and hand the session (siblings = loaded episodes) to the player. */
    fun playEpisode(episode: MediaItem, onReady: () -> Unit) {
        val s = _state.value
        val seasonId = s.selectedSeasonId ?: return
        if (s.starting) return
        lastFocusedEpisodeId = episode.id
        _state.update { it.copy(starting = true, playError = null) }
        viewModelScope.launch {
            repo.resolveEpisodeFile(seriesId, seasonId, episode.id).fold(
                onSuccess = { file ->
                    val idx = s.selectedSeasonIndex
                    val season = s.seasons.getOrNull(idx)
                    val siblings = s.episodes.items.map { it.withSeason(season, idx) }
                    val fileId = file.id.ifBlank { episode.id }
                    PlaybackHolder.start(
                        PlaybackSession.Episode(
                            s.series, seasonId, episode.withSeason(season, idx), fileId, siblings, effectiveCategory,
                            seasonNumber = season?.let { EpisodeInfo.seasonNumber(it, idx) },
                        ),
                        null,
                    )
                    _state.update { it.copy(starting = false) }
                    onReady()
                },
                onFailure = { e -> _state.update { it.copy(starting = false, playError = e.panelMessage()) } },
            )
        }
    }

    /** Play the episode in [State.continueRow] from its saved position. */
    fun resumeContinue(onReady: () -> Unit) {
        val s = _state.value
        val row = s.continueRow ?: return
        if (s.starting) return
        val meta = row.parsedMeta
        val seasonId = meta?.seasonId?.takeIf { it.isNotBlank() } ?: s.selectedSeasonId ?: return
        _state.update { it.copy(starting = true, playError = null) }
        viewModelScope.launch {
            val idx = s.seasons.indexOfFirst { it.id == seasonId }
            val season = s.seasons.getOrNull(idx)
            val loaded = if (seasonId == s.selectedSeasonId && s.episodes.hasLoaded) s.episodes.items
            else repo.episodes(seriesId, seasonId, 1, effectiveCategory).getOrNull()?.data.orEmpty()
            val episodes = loaded.map { it.withSeason(season, idx) }
            val match = SeriesProgress.findEpisode(meta, episodes)
            val fileId = listOfNotNull(meta?.playbackFileId, meta?.id, match?.id, row.mediaId.takeIf { it != seriesId })
                .firstOrNull { it.isNotBlank() }
            if (fileId == null) {
                _state.update { it.copy(starting = false, playError = "Couldn't find the episode to resume.") }
                return@launch
            }
            val episode = match ?: MediaItem(
                id = fileId,
                title = meta?.episodeTitle,
                seriesNumber = meta?.series_number ?: SeriesProgress.seasonNumber(season, idx.coerceAtLeast(0)),
                isEpisode = true,
            )
            // Without a match, siblings would point "next" at the first episode; keep it alone.
            val siblings = if (match != null) episodes else listOf(episode)
            val resumeMs = ProgressRows.resumableMs(row) ?: meta?.currentTime?.let { (it * 1000).toLong() }
            lastFocusedEpisodeId = match?.id
            val seasonNo = season?.let { EpisodeInfo.seasonNumber(it, idx.coerceAtLeast(0)) }
            PlaybackHolder.start(PlaybackSession.Episode(s.series, seasonId, episode, fileId, siblings, effectiveCategory, seasonNo), resumeMs)
            _state.update { it.copy(starting = false) }
            onReady()
        }
    }

    private fun State.withContinueEpisode(): State {
        val meta = continueRow?.parsedMeta ?: return copy(continueEpisode = null)
        val inSelected = meta.seasonId == null || meta.seasonId == selectedSeasonId
        return copy(continueEpisode = if (inSelected) SeriesProgress.findEpisode(meta, episodes.items) else null)
    }

    /** Episodes need a season number for movie-link's `series` param; fill it from the season. */
    private fun MediaItem.withSeason(season: MediaItem?, index: Int): MediaItem =
        if (seriesNumber != null) this else copy(seriesNumber = SeriesProgress.seasonNumber(season, index.coerceAtLeast(0)))

    companion object {
        const val MAX_EPISODE_PAGES = 10
    }
}
