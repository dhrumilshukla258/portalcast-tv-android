package com.portalcast.tv.ui.player

import com.portalcast.tv.ui.series.EpisodeInfo
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.MediaInfoReport
import com.portalcast.tv.data.model.downloadKey
import com.portalcast.tv.playback.SubtitleCue
import com.portalcast.tv.playback.SubtitleCues
import com.portalcast.tv.playback.SubtitlePrefs
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
    /** Left/Right held: where the bar is being moved to; the player seeks there on release. */
    val scrubMs: Long? = null,
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
    /** What the decoder is actually playing, e.g. "1080p · 23.976 fps · H.264"; null until known. */
    val videoStats: String? = null,
    /** Downloaded-subtitle line on screen now (app-rendered); null = none. */
    val subtitleText: String? = null,
    /** Viewer's timing offset for downloaded subtitles of this title (+ later, − earlier). */
    val subtitleDelayMs: Long = 0L,
    /** Downloaded subtitles are loaded for this title. */
    val externalSubtitles: Boolean = false,
    /** Subtitles side panel; null = closed. */
    val subtitlePanel: SubtitlePanelState? = null,
) {
    val isLive: Boolean get() = session is PlaybackSession.Live
    val nextEpisodeTitle: String? get() = (session as? PlaybackSession.Episode)?.let { s ->
        val ep = s.nextEpisode ?: return@let null
        val index = s.siblings.indexOfFirst { it.id == ep.id }.coerceAtLeast(0)
        "E${EpisodeInfo.number(ep, index)} · ${EpisodeInfo.title(ep, index)}"
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

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) = onVideoFormat()
        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) = onVideoFormat()
    }

    // ---- subtitles -------------------------------------------------------------------
    // Downloaded (online) subtitles are parsed and drawn by the app so their timing can be
    // moved both ways; built-in tracks are selected in ExoPlayer and drawn by PlayerView.
    private val subtitlePrefs = SubtitlePrefs(app)
    private var cues: List<SubtitleCue> = emptyList()
    private var subtitleJob: Job? = null
    private var subtitleKey: String? = null

    /** "movie:<id>" / "episode:<id>" for movies and episodes; null for live. */
    private fun titleKey(s: PlaybackSession?): String? = when (s) {
        is PlaybackSession.Movie -> "movie:" + (s.item.catalogId?.removePrefix("movie_") ?: s.item.streamId?.takeIf { it.isNotBlank() } ?: s.item.rawId)
        is PlaybackSession.Episode -> "episode:" + s.episode.id
        else -> null
    }

    private fun resetSubtitlesFor(target: PlaybackSession) {
        subtitleJob?.cancel()
        cues = emptyList()
        val key = titleKey(target)
        subtitleKey = key
        val delay = key?.let { subtitlePrefs.delayMs(it) } ?: 0L
        _state.update { it.copy(subtitleText = null, subtitleDelayMs = delay, externalSubtitles = false, subtitlePanel = null) }
        // Bring back the online subtitle chosen last time for this title.
        val subId = key?.let { subtitlePrefs.subtitleId(it) } ?: return
        viewModelScope.launch { loadOnlineSubtitle(subId, label = null, remember = false) }
    }

    /** Opens the subtitles popup (CC button), or the audio one when [audioOnly] (speaker button). */
    fun openSubtitlePanel(audioOnly: Boolean = false) {
        if (session == null || session is PlaybackSession.Live) return
        hideOverlayJob?.cancel()
        _state.update {
            it.copy(
                overlayVisible = true,
                subtitlePanel = (it.subtitlePanel ?: SubtitlePanelState(language = subtitlePrefs.language))
                    .copy(audioOnly = audioOnly, embedded = embeddedTracks(), audio = audioTracks(), activeLabel = activeLabel()),
            )
        }
    }

    /**
     * The control row under the progress bar has focus: the overlay stays up (a hide would take
     * the focused button away and leave the remote dead) until the screen hands focus back.
     */
    var controlsActive = false
        set(value) {
            field = value
            if (value) hideOverlayJob?.cancel() else scheduleOverlayHide()
        }

    /** Hides the overlay now (the controls timed out; the screen moved focus back first). */
    fun hideOverlay() {
        hideOverlayJob?.cancel()
        if (player.isPlaying) _state.update { it.copy(overlayVisible = false) }
    }

    /** "English · 5.1 · DTS" for each audio track. */
    private fun audioTracks(): List<TextTrackOption> {
        val out = ArrayList<TextTrackOption>()
        player.currentTracks.groups.forEachIndexed { gi, g ->
            if (g.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
            for (ti in 0 until g.length) {
                val f = g.getTrackFormat(ti)
                val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.let { java.util.Locale(it).displayLanguage }
                val channels = when (f.channelCount) {
                    1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"
                    else -> if (f.channelCount > 0) "${f.channelCount} ch" else null
                }
                val codec = audioCodecName(f.sampleMimeType)
                val label = listOfNotNull(f.label?.takeIf { it.isNotBlank() } ?: lang ?: "Track ${out.size + 1}", channels, codec)
                    .joinToString("  ·  ")
                out += TextTrackOption("$gi:$ti", if (g.isTrackSupported(ti)) label else "$label (not supported)", g.isTrackSelected(ti))
            }
        }
        return out
    }

    private fun audioCodecName(mime: String?): String? = when (mime) {
        androidx.media3.common.MimeTypes.AUDIO_AAC -> "AAC"
        androidx.media3.common.MimeTypes.AUDIO_AC3 -> "Dolby Digital"
        androidx.media3.common.MimeTypes.AUDIO_E_AC3, androidx.media3.common.MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital Plus"
        androidx.media3.common.MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        androidx.media3.common.MimeTypes.AUDIO_DTS, androidx.media3.common.MimeTypes.AUDIO_DTS_HD, androidx.media3.common.MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
        androidx.media3.common.MimeTypes.AUDIO_OPUS -> "Opus"
        androidx.media3.common.MimeTypes.AUDIO_MPEG -> "MP3"
        androidx.media3.common.MimeTypes.AUDIO_FLAC -> "FLAC"
        else -> null
    }

    fun selectAudioTrack(id: String) {
        val (gi, ti) = id.split(":").map { it.toInt() }
        val group = player.currentTracks.groups.getOrNull(gi) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, listOf(ti)))
            .build()
        closeSubtitlePanel()
    }

    fun closeSubtitlePanel() {
        _state.update { it.copy(subtitlePanel = null) }
        scheduleOverlayHide()
    }

    private fun embeddedTracks(): List<TextTrackOption> {
        val out = ArrayList<TextTrackOption>()
        player.currentTracks.groups.forEachIndexed { gi, g ->
            if (g.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (ti in 0 until g.length) {
                val f = g.getTrackFormat(ti)
                val name = f.label?.takeIf { it.isNotBlank() }
                    ?: f.language?.let { java.util.Locale(it).displayLanguage.takeIf { d -> d.isNotBlank() } }
                    ?: "Track ${out.size + 1}"
                out += TextTrackOption("$gi:$ti", name, g.isTrackSelected(ti))
            }
        }
        return out
    }

    private fun activeLabel(): String = when {
        _state.value.externalSubtitles -> "Online subtitles"
        else -> embeddedTracks().firstOrNull { it.selected }?.let { "${it.label} (built-in)" } ?: "Off"
    }

    fun subtitlesOff() {
        clearOnline()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        closeSubtitlePanel()
    }

    fun selectEmbeddedSubtitle(id: String) {
        val (gi, ti) = id.split(":").map { it.toInt() }
        val group = player.currentTracks.groups.getOrNull(gi) ?: return
        clearOnline()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, listOf(ti)))
            .build()
        closeSubtitlePanel()
    }

    private fun clearOnline() {
        subtitleJob?.cancel()
        cues = emptyList()
        subtitleKey?.let { subtitlePrefs.setSubtitleId(it, null) }
        _state.update { it.copy(subtitleText = null, externalSubtitles = false) }
    }

    fun cycleSubtitleLanguage() {
        val langs = SubtitlePrefs.LANGUAGES.map { it.first }
        val next = langs[(langs.indexOf(subtitlePrefs.language) + 1).mod(langs.size)]
        subtitlePrefs.language = next
        _state.update { st -> st.subtitlePanel?.let { st.copy(subtitlePanel = it.copy(language = next, results = null, message = null)) } ?: st }
    }

    fun searchOnlineSubtitles() {
        val s = session ?: return
        val (title, year, season, episode) = when (s) {
            is PlaybackSession.Movie -> SearchQuery(s.item.displayTitle, s.item.displayYear, null, null)
            is PlaybackSession.Episode -> {
                val idx = s.siblings.indexOfFirst { it.id == s.episode.id }.coerceAtLeast(0)
                SearchQuery(s.series.displayTitle, null, s.seasonNumber, EpisodeInfo.number(s.episode, idx))
            }
            is PlaybackSession.Live -> return
        }
        val lang = subtitlePrefs.language
        _state.update { st -> st.subtitlePanel?.let { st.copy(subtitlePanel = it.copy(searching = true, message = null)) } ?: st }
        viewModelScope.launch {
            val result = runCatching { container.api.searchSubtitles(title, year, season, episode, lang) }
            _state.update { st ->
                val p = st.subtitlePanel ?: return@update st
                result.fold(
                    onSuccess = { r ->
                        st.copy(subtitlePanel = p.copy(searching = false, results = r.results,
                            message = if (r.results.isEmpty()) "Nothing found. The server needs an OpenSubtitles API key for online search." else null))
                    },
                    onFailure = { st.copy(subtitlePanel = p.copy(searching = false, message = "Search failed: ${ApiError.from(it).message}")) },
                )
            }
        }
    }

    private data class SearchQuery(val title: String, val year: String?, val season: Int?, val episode: Int?)

    fun pickOnlineSubtitle(r: com.portalcast.tv.data.model.SubtitleResult) {
        viewModelScope.launch { loadOnlineSubtitle(r.downloadKey, label = r.releaseName, remember = true) }
    }

    private suspend fun loadOnlineSubtitle(subId: String, label: String?, remember: Boolean) {
        _state.update { st -> st.subtitlePanel?.let { st.copy(subtitlePanel = it.copy(loadingFile = subId)) } ?: st }
        val osFileId = subId.removePrefix("os:").takeIf { subId.startsWith("os:") }?.toLongOrNull()
        val text = runCatching { container.api.downloadSubtitle(subId, osFileId).use { it.string() } }
        val parsed = text.getOrNull()?.let { SubtitleCues.parse(it) }.orEmpty()
        if (parsed.isEmpty()) {
            _state.update { st -> st.subtitlePanel?.let { st.copy(subtitlePanel = it.copy(loadingFile = null, message = "Couldn't load that subtitle. Try another.")) } ?: st }
            return
        }
        // Online subtitles replace any built-in track.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        cues = parsed
        if (remember) subtitleKey?.let { subtitlePrefs.setSubtitleId(it, subId) }
        _state.update { st ->
            st.copy(externalSubtitles = true,
                subtitlePanel = st.subtitlePanel?.copy(loadingFile = null, activeLabel = "Online · ${label ?: "subtitle"}"))
        }
        subtitleJob?.cancel()
        subtitleJob = viewModelScope.launch {
            var last: String? = null
            while (isActive) {
                val cue = SubtitleCues.at(cues, player.currentPosition, _state.value.subtitleDelayMs)
                val text = cue?.text
                if (text != last) { last = text; _state.update { it.copy(subtitleText = text) } }
                delay(80)
            }
        }
    }

    fun adjustSubtitleDelay(deltaMs: Long) {
        val next = (_state.value.subtitleDelayMs + deltaMs).coerceIn(-60_000L, 60_000L)
        _state.update { it.copy(subtitleDelayMs = next) }
        subtitleKey?.let { subtitlePrefs.setDelayMs(it, next) }
    }

    fun resetSubtitleDelay() = adjustSubtitleDelay(-_state.value.subtitleDelayMs)

    // ---- measured video quality ------------------------------------------------------
    private var reportedKey: String? = null

    /** Shows what is decoded and reports it once per title so catalog badges are truthful. */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun onVideoFormat() {
        val fmt = player.videoFormat ?: return
        val w = fmt.width.takeIf { it > 0 } ?: player.videoSize.width.takeIf { it > 0 } ?: return
        val h = fmt.height.takeIf { it > 0 } ?: player.videoSize.height.takeIf { it > 0 } ?: return
        val fps = fmt.frameRate.takeIf { it > 0f }?.let { Math.round(it * 1000.0) / 1000.0 }
        val codec = VideoStats.codecName(fmt.sampleMimeType, fmt.codecs)
        _state.update { it.copy(videoStats = VideoStats.describe(w, h, fps, codec)) }

        val s = session ?: return
        val (type, id) = when (s) {
            is PlaybackSession.Movie -> "movie" to (s.item.catalogId?.removePrefix("movie_") ?: s.item.streamId?.takeIf { it.isNotBlank() } ?: s.item.rawId)
            is PlaybackSession.Episode -> "episode" to s.episode.id
            is PlaybackSession.Live -> return
        }
        if (id.isBlank()) return
        val key = "$type:$id:${w}x$h:$fps"
        if (key == reportedKey) return
        reportedKey = key
        container.appScope.launch {
            runCatching { container.api.reportMediaInfo(MediaInfoReport(type, id, w, h, fps, codec)).close() }
        }
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
        _state.update { it.copy(videoStats = null) }
        resetSubtitlesFor(target)
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

    private var scrubStartMs = 0L
    private var scrubLastMs = 0L

    /**
     * Left/Right held (VOD): the bar moves without seeking, speeding up to a steady top speed
     * that depends on the title's length (scrubStepMs); [scrubRelease] seeks once. Each key repeat used to seek,
     * which rebuffered an IPTV stream at every step.
     */
    fun scrubHeld(forward: Boolean) {
        val s = session ?: return
        if (s.isLive) { showOverlay(); return }
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: return
        val now = android.os.SystemClock.uptimeMillis()
        val from = _state.value.scrubMs
        if (from == null) {
            scrubStartMs = now
            scrubLastMs = now
            _state.update { it.copy(scrubMs = player.currentPosition) }
        } else {
            val delta = scrubStepMs(heldMs = now - scrubStartMs, sinceLastMs = now - scrubLastMs, durationMs = duration)
            scrubLastMs = now
            _state.update { it.copy(scrubMs = (from + if (forward) delta else -delta).coerceIn(0L, duration)) }
        }
        showOverlay()
    }

    /** The held key let go: one seek to where the bar is. False when nothing was being moved. */
    fun scrubRelease(): Boolean {
        val target = _state.value.scrubMs ?: return false
        player.seekTo(target)
        _state.update { it.copy(scrubMs = null, positionMs = target) }
        showOverlay()
        return true
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
            if (player.isPlaying && _state.value.pendingChannel == null && !controlsActive) _state.update { it.copy(overlayVisible = false) }
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
        val nearEnd = s is PlaybackSession.Episode && s.nextEpisode != null && dur > 0 && dur - pos <= nextEpisodeWindowMs(dur)
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
        // The server answers 404 "Failed to fetch stream" / 500 "Failed to generate URL" when the
        // provider no longer serves this title or channel: say so in plain words.
        httpCode == 404 -> if (session is PlaybackSession.Live) "This channel isn't available from your provider right now." else "This title isn't available from your provider right now."
        httpCode != null && httpCode >= 500 -> "Your provider couldn't start this stream (error $httpCode). Try again later."
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
        /**
         * Holding Left/Right: the bar starts at 30x and speeds up over 3 s to a top speed that
         * crosses the whole title in about 10 s (at least 120x), then stays there. A 2-hour film
         * tops out at 720x, 12 minutes a second (owner, 2026-10-25: 60x took 2 minutes of holding).
         */
        const val SCRUB_START_SPEED = 30f
        const val SCRUB_MIN_TOP_SPEED = 120f
        const val SCRUB_CROSS_MS = 10_000L
        const val SCRUB_RAMP_MS = 3_000L

        /** Top speed for a title of [durationMs]. */
        fun scrubTopSpeed(durationMs: Long): Float = maxOf(SCRUB_MIN_TOP_SPEED, durationMs.toFloat() / SCRUB_CROSS_MS)

        /** Video time to move for [sinceLastMs] of real time, [heldMs] into the hold. */
        fun scrubStepMs(heldMs: Long, sinceLastMs: Long, durationMs: Long): Long {
            val ramp = (heldMs.toFloat() / SCRUB_RAMP_MS).coerceIn(0f, 1f)
            // Eased in (ramp squared): the first second stays near the start speed, so short holds are precise.
            val speed = SCRUB_START_SPEED + (scrubTopSpeed(durationMs) - SCRUB_START_SPEED) * ramp * ramp
            // A late key repeat (a busy frame) never jumps more than a quarter second's worth.
            return (speed * sinceLastMs.coerceIn(0L, 250L)).toLong()
        }
        const val OVERLAY_HIDE_MS = 4_000L
        const val CHANNEL_SWITCH_DEBOUNCE_MS = 2_000L
        /**
         * How long before the end the "Next episode" button shows: the last 5% of the episode,
         * kept between 30 s and 90 s (22-min sitcom ~66 s, 45-min drama 90 s). Was a flat 5 min,
         * a quarter of a sitcom. Playback still moves on by itself only when the episode ends.
         */
        fun nextEpisodeWindowMs(durationMs: Long): Long = (durationMs / 20).coerceIn(30_000L, 90_000L)
        @Suppress("unused") const val MAX_LOAD_RETRIES = PortalcastLoadErrorPolicy.MAX_RETRIES
    }
}
