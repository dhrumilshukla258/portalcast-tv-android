package com.portalcast.tv.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.repo.UserRepository
import com.portalcast.tv.data.repo.VodRepository
import com.portalcast.tv.playback.PlaybackHolder
import com.portalcast.tv.playback.PlaybackSession
import com.portalcast.tv.ui.SelectedItemHolder
import com.portalcast.tv.ui.components.ProgressRows
import com.portalcast.tv.ui.components.panelMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Movie detail. The display item comes from [SelectedItemHolder] (the list/Discover item
 * the user clicked); the playable file from `VodRepository.resolveFile`, which also fills
 * fields the list item lacks and is the whole item when the holder is empty.
 * The file is always resolved; a Discover-resolved item (id already the file id) is the fallback.
 */
class MovieDetailViewModel(
    private val movieId: String,
    private val category: String,
    private val vod: VodRepository,
    private val users: UserRepository,
) : ViewModel() {

    data class State(
        val item: MediaItem? = null,
        val loading: Boolean = true,
        val error: Throwable? = null,
        val file: MediaItem? = null,
        val progressLoaded: Boolean = false,
        val resumeMs: Long? = null,
        val starting: Boolean = false,
        val playError: String? = null,
    )

    private val held: MediaItem? = SelectedItemHolder.get(movieId)
    private val _state = MutableStateFlow(State(item = held, loading = held == null))
    val state: StateFlow<State> = _state

    /** The real category to send along: the route's, or the item's own when the route said `*`. */
    private val effectiveCategory: String?
        get() = category.takeUnless { it.isBlank() || it == "*" } ?: _state.value.item?.categoryId?.takeIf { it.isNotBlank() }

    /** Set by the screen on its first showing; later showings (back from the player) refresh progress. */
    var shownOnce = false

    init { load() }

    fun load() {
        // Always resolve the real file. A `cmd` on the item is NOT proof it is already a file:
        // on Stalker every list item carries one, and playing the list id made the portal answer
        // "nothing_to_play" (found on a real portal). Only when the lookup fails and the item has
        // a cmd (a Discover-resolved item, whose id already IS the file id) is the item used as is.
        _state.update { it.copy(loading = it.item == null, error = null) }
        viewModelScope.launch {
            vod.resolveFile(movieId, effectiveCategory).fold(
                onSuccess = { file ->
                    _state.update { s -> s.copy(loading = false, file = file, item = s.item?.mergedWith(file) ?: file) }
                },
                onFailure = { e ->
                    _state.update { s ->
                        val held = s.item
                        if (held?.cmd != null) s.copy(loading = false, file = held)
                        // With a held item the screen still works; Play retries the resolve.
                        else s.copy(loading = false, error = if (held == null) e else null)
                    }
                },
            )
        }
        refreshProgress()
    }

    /** Re-read progress (on every (re)entry, so Resume reflects what was just watched). */
    fun refreshProgress() {
        viewModelScope.launch {
            val rows = users.progress(forceRefresh = true).getOrNull()
            val id = _state.value.item?.id?.takeIf { it.isNotBlank() } ?: movieId
            _state.update { it.copy(progressLoaded = true, resumeMs = rows?.let { r -> ProgressRows.resumableMs(r, id) }) }
        }
    }

    /**
     * Resolve the file if needed, hand the session to the player and call [onReady]
     * (navigate). [fromStart] passes 0 so the player does not auto-resume.
     */
    fun play(fromStart: Boolean, onReady: () -> Unit) {
        val s = _state.value
        val item = s.item ?: return
        if (s.starting) return
        _state.update { it.copy(starting = true, playError = null) }
        viewModelScope.launch {
            val fileId: String? = when {
                s.file != null -> s.file.id
                else -> vod.resolveFile(movieId, effectiveCategory).fold(
                    onSuccess = { file -> _state.update { it.copy(file = file) }; file.id },
                    onFailure = { e ->
                        if (item.cmd != null) item.id // Discover-resolved: the id is the file id
                        else { _state.update { it.copy(starting = false, playError = e.panelMessage()) }; null }
                    },
                )
            }
            if (fileId == null) return@launch
            if (fileId.isBlank()) {
                _state.update { it.copy(starting = false, playError = "This movie has no playable file.") }
                return@launch
            }
            val resume = if (fromStart) 0L else s.resumeMs
            PlaybackHolder.start(PlaybackSession.Movie(item, fileId, effectiveCategory), resume)
            _state.update { it.copy(starting = false) }
            onReady()
        }
    }
}

/**
 * The list item with gaps filled from the resolved file. The title, id and art stay the
 * list item's (a file's `name` is often a quality string like "1080p").
 */
fun MediaItem.mergedWith(file: MediaItem): MediaItem = copy(
    description = description ?: file.description,
    plot = plot ?: file.plot,
    director = director ?: file.director,
    actors = actors ?: file.actors,
    cast = cast ?: file.cast,
    year = year ?: file.year,
    rating = rating ?: file.rating,
    ratingImdb = ratingImdb ?: file.ratingImdb,
    genresStr = genresStr ?: file.genresStr,
    country = country ?: file.country,
    duration = duration ?: file.duration,
    runtime = runtime ?: file.runtime,
    screenshotUri = screenshotUri ?: file.screenshotUri,
    backdropPath = backdropPath ?: file.backdropPath,
    backdropHdPath = backdropHdPath ?: file.backdropHdPath,
    trailerKey = trailerKey ?: file.trailerKey,
    categoryId = categoryId ?: file.categoryId,
)
