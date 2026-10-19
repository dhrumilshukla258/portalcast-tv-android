package com.portalcast.tv.ui.series

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

    init {
        viewModelScope.launch {
            loadProgressNow()
            loadSeasons()
        }
    }

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
                        PlaybackSession.Episode(s.series, seasonId, episode.withSeason(season, idx), fileId, siblings, effectiveCategory),
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
            PlaybackHolder.start(PlaybackSession.Episode(s.series, seasonId, episode, fileId, siblings, effectiveCategory), resumeMs)
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
