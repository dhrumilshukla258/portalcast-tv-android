package com.portalcast.tv.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.net.UserAgent
import com.portalcast.tv.playback.DefaultProgressReporter
import com.portalcast.tv.playback.PlaybackHolder
import com.portalcast.tv.playback.PlaybackSession
import com.portalcast.tv.playback.PlayerFactory
import com.portalcast.tv.playback.PortalcastLoadErrorPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PlayerStatus { Loading, Buffering, Playing, Paused, Ended }

data class PlayerUiState(
    val session: PlaybackSession? = null,
    val status: PlayerStatus = PlayerStatus.Loading,
    /** Readable failure for the error panel; null while playback is healthy. */
    val error: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    /** The info overlay / channel banner is shown; auto-hides 4 s after the last key. */
    val overlayVisible: Boolean = true,
    /** A 429 was seen by the load policy and ExoPlayer is backing off. */
    val rateLimited: Boolean = false,
    /** Live: the channel picked with up/down that will load once the 2 s debounce elapses. */
    val pendingChannel: Channel? = null,
    /** Episode: ≤ 5 min remain and there is a next episode. */
    val showNextEpisode: Boolean = false,
    val nextEpisodeLoading: Boolean = false,
    /** A 401/403 is being handled by re-resolving the stream. */
    val reauthorizing: Boolean = false,
) {
    val isLive: Boolean get() = session is PlaybackSession.Live
    val nextEpisodeTitle: String? get() = (session as? PlaybackSession.Episode)?.nextEpisode?.let { ep ->
        ep.displayTitle.ifBlank { ep.episodeNum?.let { "Episode $it" } ?: "Next episode" }
    }
}

/**
 * Owns the ExoPlayer for the player route so an Activity recreation does not
 * restart playback. Input arrives through [PlaybackHolder]; the screen only
 * forwards key presses and renders [state].
 *
 * Error handling (contract §5): the load policy makes 401/403 fatal at once;
 * [onPlayerError] then re-resolves exactly once — VOD via
 * `StreamResolver.resolve(forceRefresh = true)` and a seek back to the last
 * position, Live via [liveChannelRefresher] (the caller refetches `/api/v2/channels`
 * and hands back the channel with a fresh `cmd`). A second failure shows the error
 * panel. 429/404/502 are retried with back-off inside ExoPlayer; only when the 10
 * attempts are spent does the panel appear.
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.appContainer
    private val resolver = container.streamResolver
    private val reporter = container.progressReporter
    private val seriesRepository = container.seriesRepository
    private val factory = PlayerFactory(app, container.mediaClient, UserAgent.value)

    val player: ExoPlayer = factory.create { code, _, delayMs ->
        if (code == 429) _state.update { it.copy(rateLimited = delayMs != C.TIME_UNSET) }
    }

    /**
     * Optional: given the channel whose stream answered 401/403, refetch the channel
     * list and return the same channel with a new `cmd` (null = could not). Set by the
     * screen from its caller; without it a live auth failure shows the error panel.
     */
    @Volatile var liveChannelRefresher: (suspend (Channel) -> Channel?)? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _navigateBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateBack: SharedFlow<Unit> = _navigateBack.asSharedFlow()

    private var session: PlaybackSession? = null
    private var loadJob: Job? = null
    private var hideOverlayJob: Job? = null
    private var channelSwitchJob: Job? = null
    private var pendingIndex = -1
    private var reResolved = false
    private var completed = false
    private var exitFlushed = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> _state.update { if (it.status == PlayerStatus.Loading) it else it.copy(status = PlayerStatus.Buffering) }
                Player.STATE_READY -> {
                    reResolved = false
                    _state.update {
                        it.copy(
                            status = if (player.playWhenReady) PlayerStatus.Playing else PlayerStatus.Paused,
                            error = null, rateLimited = false, reauthorizing = false,
                        )
                    }
                    scheduleOverlayHide()
                }
                Player.STATE_ENDED -> onEnded()
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (player.playbackState != Player.STATE_READY) return
            _state.update { it.copy(status = if (isPlaying) PlayerStatus.Playing else PlayerStatus.Paused) }
            if (isPlaying) scheduleOverlayHide() else showOverlay(autoHide = false)
        }

        override fun onPlayerError(error: PlaybackException) = handleError(error)
    }

    init {
        player.addListener(listener)
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                tick()
            }
        }
    }

    // ---- entry -----------------------------------------------------------------

    /** Idempotent: a recreated screen calling this with the same session does nothing. */
    fun start(session: PlaybackSession, resumeFromMs: Long?) {
        if (this.session == session && _state.value.error == null) return
        beginSession(session, resumeFromMs, forceRefresh = false)
    }

    private fun beginSession(newSession: PlaybackSession, resumeFromMs: Long?, forceRefresh: Boolean) {
        session = newSession
        completed = false
        reResolved = false
        exitFlushed = false
        pendingIndex = -1
        channelSwitchJob?.cancel()
        _state.update {
            PlayerUiState(session = newSession, status = PlayerStatus.Loading, overlayVisible = true)
        }
        load(newSession, resumeFromMs, forceRefresh)
    }

    private fun load(target: PlaybackSession, resumeFromMs: Long?, forceRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val resume = resumeFromMs ?: if (target.isLive) null else reporter.resumePositionMs(target)
            val resolved = resolver.resolve(target, forceRefresh)
            if (session != target) return@launch
            resolved.fold(
                onSuccess = { stream ->
                    player.setMediaItem(factory.mediaItem(stream))
                    if (resume != null && resume > 0) player.seekTo(resume)
                    player.prepare()
                    player.playWhenReady = true
                    _state.update { it.copy(status = PlayerStatus.Loading, error = null) }
                },
                onFailure = { t -> fail(messageFor(t)) },
            )
        }
    }

    // ---- key actions ---------------------------------------------------------------

    fun togglePlayPause() {
        if (player.isPlaying) pause() else play()
    }

    fun play() {
        if (player.playbackState == Player.STATE_IDLE) { retry(); return }
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
        showOverlay()
    }

    fun pause() {
        player.pause()
        showOverlay(autoHide = false)
        flushAsync()
    }

    /** VOD only. */
    fun seekBy(deltaMs: Long) {
        val s = session ?: return
        if (s.isLive) { showOverlay(); return }
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: return
        val target = (player.currentPosition + deltaMs).coerceIn(0L, duration)
        player.seekTo(target)
        _state.update { it.copy(positionMs = target) }
        showOverlay()
    }

    /** Live only: step through the session playlist; the switch happens after a 2 s pause in key presses. */
    fun channelStep(delta: Int) {
        val s = session as? PlaybackSession.Live ?: return
        val playlist = s.playlist
        if (playlist.size < 2) { showOverlay(); return }
        val base = if (pendingIndex >= 0) pendingIndex else playlist.indexOfFirst { it.id == s.channel.id }.coerceAtLeast(0)
        pendingIndex = Math.floorMod(base + delta, playlist.size)
        val target = playlist[pendingIndex]
        _state.update { it.copy(pendingChannel = target) }
        showOverlay(autoHide = false)
        channelSwitchJob?.cancel()
        channelSwitchJob = viewModelScope.launch {
            delay(CHANNEL_SWITCH_DEBOUNCE_MS)
            switchToChannel(target, playlist)
        }
    }

    private fun switchToChannel(channel: Channel, playlist: List<Channel>) {
        val next = PlaybackSession.Live(channel, playlist)
        PlaybackHolder.start(next)
        player.stop()
        beginSession(next, resumeFromMs = null, forceRefresh = false)
    }

    fun showOverlay(autoHide: Boolean = true) {
        _state.update { it.copy(overlayVisible = true) }
        if (autoHide) scheduleOverlayHide() else hideOverlayJob?.cancel()
    }

    fun toggleOverlay() {
        if (_state.value.overlayVisible) {
            hideOverlayJob?.cancel()
            _state.update { it.copy(overlayVisible = false) }
        } else showOverlay()
    }

    private fun scheduleOverlayHide() {
        hideOverlayJob?.cancel()
        hideOverlayJob = viewModelScope.launch {
            delay(OVERLAY_HIDE_MS)
            if (player.isPlaying && _state.value.pendingChannel == null) _state.update { it.copy(overlayVisible = false) }
        }
    }

    /** "Next episode" button, or auto-advance on ended. */
    fun playNextEpisode() {
        val s = session as? PlaybackSession.Episode ?: return
        val next = s.nextEpisode ?: return
        if (_state.value.nextEpisodeLoading) return
        _state.update { it.copy(nextEpisodeLoading = true) }
        viewModelScope.launch {
            if (!completed) flushNow()
            seriesRepository.resolveEpisodeFile(s.series.id, s.seasonId, next.id).fold(
                onSuccess = { file ->
                    val fileId = file.id.ifBlank { next.id }
                    val nextSession = s.copy(episode = next, fileId = fileId)
                    PlaybackHolder.start(nextSession)
                    player.stop()
                    beginSession(nextSession, resumeFromMs = null, forceRefresh = false)
                },
                onFailure = { t ->
                    _state.update { it.copy(nextEpisodeLoading = false) }
                    fail("Could not load the next episode: ${messageFor(t)}")
                },
            )
        }
    }

    /** Error panel "Retry": re-resolve with a fresh token and continue from the last position. */
    fun retry() {
        val s = session ?: return
        val pos = player.currentPosition.takeIf { it > 0 && !s.isLive }
        reResolved = false
        _state.update { it.copy(error = null, status = PlayerStatus.Loading, rateLimited = false) }
        load(s, pos, forceRefresh = true)
    }

    /** Back: persist the position (on a scope that outlives this ViewModel) and let the screen pop. */
    fun flushForExit() {
        if (exitFlushed) return
        exitFlushed = true
        val s = session ?: return
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        player.pause()
        if (!s.isLive && !completed) container.appScope.launch { reporter.flush(s, pos, dur) }
    }

    // ---- internals -------------------------------------------------------------------

    private fun tick() {
        val s = session ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val buffered = player.bufferedPosition.coerceAtLeast(0L)
        val nearEnd = s is PlaybackSession.Episode && s.nextEpisode != null && dur > 0 && dur - pos <= NEXT_EPISODE_WINDOW_MS
        _state.update { it.copy(positionMs = pos, durationMs = dur, bufferedMs = buffered, showNextEpisode = nearEnd) }
        if (s.isLive) return
        if (player.isPlaying) reporter.onProgress(s, pos, dur)
        if (dur > 0 && !completed && pos.toDouble() / dur >= DefaultProgressReporter.COMPLETION_FRACTION) {
            completed = true
            viewModelScope.launch { reporter.markCompleted(s, dur) }
        }
    }

    private fun onEnded() {
        val s = session ?: return
        _state.update { it.copy(status = PlayerStatus.Ended, overlayVisible = true) }
        when (s) {
            is PlaybackSession.Live -> load(s, null, forceRefresh = false)
            is PlaybackSession.Movie -> viewModelScope.launch { completeThen { _navigateBack.tryEmit(Unit) } }
            is PlaybackSession.Episode -> viewModelScope.launch {
                completeThen { if (s.nextEpisode != null) playNextEpisode() else _navigateBack.tryEmit(Unit) }
            }
        }
    }

    private suspend fun completeThen(block: () -> Unit) {
        val s = session ?: return
        if (!completed) {
            completed = true
            val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            reporter.markCompleted(s, dur)
        }
        block()
    }

    private fun handleError(error: PlaybackException) {
        val s = session ?: return
        // Live HLS fell behind the server's playlist window (slow device, long pause/buffering):
        // the documented recovery is to jump back to the live edge and carry on, not an error.
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        val code = PlayerFactory.httpStatusOf(error)
        if ((code == 401 || code == 403) && !reResolved) {
            reResolved = true
            val pos = player.currentPosition.takeIf { it > 0 }
            _state.update { it.copy(reauthorizing = true, status = PlayerStatus.Loading) }
            when (s) {
                is PlaybackSession.Live -> {
                    val refresher = liveChannelRefresher
                    if (refresher == null) { fail("Channel authorization expired (HTTP $code). Reopen the channel."); return }
                    viewModelScope.launch {
                        val fresh = runCatching { refresher(s.channel) }.getOrNull()
                        if (fresh?.cmd.isNullOrBlank()) { fail("Channel authorization expired (HTTP $code). Reopen the channel."); return@launch }
                        val playlist = s.playlist.map { if (it.id == fresh!!.id) fresh else it }
                        val next = PlaybackSession.Live(fresh!!, playlist)
                        session = next
                        PlaybackHolder.start(next)
                        _state.update { it.copy(session = next) }
                        load(next, null, forceRefresh = true)
                    }
                }
                else -> load(s, pos, forceRefresh = true)
            }
            return
        }
        fail(messageFor(error, code))
    }

    private fun fail(message: String) {
        channelSwitchJob?.cancel()
        _state.update { it.copy(error = message, status = PlayerStatus.Paused, overlayVisible = true, reauthorizing = false, pendingChannel = null, nextEpisodeLoading = false) }
    }

    private fun messageFor(t: Throwable, httpCode: Int? = null): String = when {
        t is ApiError.RateLimited || httpCode == 429 -> "Server is rate limiting, retrying…"
        httpCode == 401 || httpCode == 403 -> "Stream authorization expired (HTTP $httpCode)"
        httpCode == 404 -> "Stream not found (HTTP 404)"
        httpCode != null && httpCode >= 500 -> "Upstream error (HTTP $httpCode)"
        t is ApiError -> t.message ?: "Request failed"
        t is PlaybackException -> when (t.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network error while streaming"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "The stream could not be parsed"
            else -> t.errorCodeName.removePrefix("ERROR_CODE_").replace('_', ' ').lowercase().replaceFirstChar { c -> c.uppercase() }
        }
        else -> t.message ?: "Playback failed"
    }

    private fun flushAsync() {
        val s = session ?: return
        if (s.isLive || completed) return
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        viewModelScope.launch { reporter.flush(s, pos, dur) }
    }

    private suspend fun flushNow() {
        val s = session ?: return
        if (s.isLive) return
        val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        reporter.flush(s, player.currentPosition, dur)
    }

    override fun onCleared() {
        flushForExit()
        player.removeListener(listener)
        player.release()
        super.onCleared()
    }

    companion object {
        const val SEEK_STEP_MS = 10_000L
        const val SEEK_STEP_FAST_MS = 30_000L
        const val OVERLAY_HIDE_MS = 4_000L
        const val CHANNEL_SWITCH_DEBOUNCE_MS = 2_000L
        const val NEXT_EPISODE_WINDOW_MS = 5 * 60_000L
        @Suppress("unused") const val MAX_LOAD_RETRIES = PortalcastLoadErrorPolicy.MAX_RETRIES
    }
}
