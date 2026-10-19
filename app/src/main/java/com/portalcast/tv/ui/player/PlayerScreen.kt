@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.portalcast.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.EpgProgramme
import com.portalcast.tv.playback.PlaybackHolder
import com.portalcast.tv.playback.PlaybackSession
import com.portalcast.tv.ui.series.EpisodeInfo
import com.portalcast.tv.ui.theme.AppColors

/**
 * Full-screen player route. Input comes from [PlaybackHolder] (set by the browse
 * screen before navigating here), playback state from [PlayerViewModel].
 *
 * D-pad: Center/Enter = play/pause; Left/Right = ±10 s (±30 s on a held key) for
 * VOD; Up/Down = previous/next channel in the live playlist (2 s debounce);
 * Info/Menu toggles the overlay; Back flushes progress and pops.
 *
 * @param nowNext optional EPG lookup for the live banner (current + next programme).
 * @param refreshLiveChannel optional: on a stream 401/403 for a live channel, refetch the
 *   channel list and return the same channel with a fresh `cmd` (contract §5.1).
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    nowNext: ((Channel) -> Pair<EpgProgramme?, EpgProgramme?>)? = null,
    refreshLiveChannel: (suspend (Channel) -> Channel?)? = null,
    vm: PlayerViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val rootFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }

    SideEffect { vm.liveChannelRefresher = refreshLiveChannel }

    LaunchedEffect(Unit) {
        val session = PlaybackHolder.current
        if (session == null) onBack() else vm.start(session, PlaybackHolder.resumeFromMs)
    }
    LaunchedEffect(Unit) { vm.navigateBack.collect { onBack() } }

    val exit = { vm.flushForExit(); onBack() }
    BackHandler(onBack = exit)
    BackHandler(enabled = state.subtitlePanel != null) { vm.closeSubtitlePanel() }

    val hasError = state.error != null
    // Request focus after the first frame and retry until it sticks: a request made during the
    // first composition (before attach) silently failed, so the first remote key was spent just
    // moving focus into the player (the first channel-down press did nothing).
    var rootHasFocus by remember { mutableStateOf(false) }
    // Focus never jumps to the "Next episode" button by itself: with it focused, the OK press
    // meant to pause started the next episode minutes early. Down selects it instead.
    LaunchedEffect(hasError) {
        // Up to ~5 s: during the navigation transition the previous screen can still hold focus,
        // and on a real portal (slow first load) 0.7 s was not enough, so channel up/down was dead.
        repeat(50) {
            withFrameNanos { }
            val ok = runCatching {
                when {
                    hasError -> retryFocus.requestFocus()
                    else -> rootFocus.requestFocus()
                }
            }.isSuccess
            delay(100)
            if (ok && (hasError || rootHasFocus)) return@LaunchedEffect
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // onKeyEvent must sit BEFORE focusable(): modifiers after the focus target are its
            // children and never see key events, which left every remote key dead in the player.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || hasError || state.subtitlePanel != null) return@onKeyEvent false
                val live = state.isLive
                val held = event.nativeKeyEvent.repeatCount >= 3
                val step = if (held) PlayerViewModel.SEEK_STEP_FAST_MS else PlayerViewModel.SEEK_STEP_MS
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> { vm.togglePlayPause(); true }
                    Key.MediaPlay -> { vm.play(); true }
                    Key.MediaPause -> { vm.pause(); true }
                    Key.MediaStop -> { exit(); true }
                    Key.DirectionLeft, Key.MediaRewind -> { if (live) vm.showOverlay() else vm.seekBy(-step); true }
                    Key.DirectionRight, Key.MediaFastForward -> { if (live) vm.showOverlay() else vm.seekBy(step); true }
                    // Up opens the subtitles panel for movies/episodes (and shows the info overlay).
                    Key.DirectionUp -> { if (live) vm.channelStep(-LIVE_DOWN_KEY_DELTA) else { vm.showOverlay(); vm.openSubtitlePanel() }; true }
                    Key.Captions -> { if (!live) vm.openSubtitlePanel(); true }
                    Key.DirectionDown -> {
                        when {
                            live -> vm.channelStep(LIVE_DOWN_KEY_DELTA)
                            state.showNextEpisode -> runCatching { nextFocus.requestFocus() }
                            else -> vm.showOverlay()
                        }
                        true
                    }
                    Key.ChannelUp -> { if (live) vm.channelStep(1); true }
                    Key.ChannelDown -> { if (live) vm.channelStep(-1); true }
                    Key.Info, Key.Menu -> { vm.toggleOverlay(); true }
                    else -> false
                }
            }
            .onFocusChanged { rootHasFocus = it.isFocused }
            .focusRequester(rootFocus)
            // While the error panel is up the root must not hold focus: arrow keys never move
            // focus from a parent into its children, so Retry/Back were unreachable.
            .focusable(enabled = !hasError),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    // Defensive: keep the Android view out of focus so the remote always reaches
                    // the Compose key handler on the root Box.
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = vm.player
                }
            },
            update = { view -> if (view.player !== vm.player) view.player = vm.player },
            onRelease = { view -> view.player = null },
        )

        val session = state.session
        when {
            hasError -> ErrorPanel(message = state.error.orEmpty(), retryFocus = retryFocus, onRetry = vm::retry, onBack = exit)
            session is PlaybackSession.Live -> {
                if (state.overlayVisible || state.pendingChannel != null) {
                    LiveBanner(
                        channel = state.pendingChannel ?: session.channel,
                        pending = state.pendingChannel != null,
                        programmes = nowNext?.invoke(state.pendingChannel ?: session.channel),
                        videoStats = state.videoStats.takeIf { state.pendingChannel == null },
                    )
                }
            }
            session != null -> {
                if (state.overlayVisible) VodOverlay(state)
                if (state.showNextEpisode) {
                    NextEpisodeButton(
                        title = state.nextEpisodeTitle ?: "Next episode",
                        loading = state.nextEpisodeLoading,
                        focusRequester = nextFocus,
                        onClick = vm::playNextEpisode,
                        onLeave = { runCatching { rootFocus.requestFocus() } },
                    )
                }
            }
        }

        if (!hasError) StatusPills(state)
        state.subtitleText?.let { SubtitleLine(it) }
        state.subtitlePanel?.let { SubtitlePanel(it, state.subtitleDelayMs, state.externalSubtitles, vm) }

    }
}

/** DPAD_DOWN steps forward through the playlist (like a channel list); flip the sign to invert. */
private const val LIVE_DOWN_KEY_DELTA = 1

// ---- overlays --------------------------------------------------------------------

@Composable
private fun BoxScope.StatusPills(state: PlayerUiState) {
    val busy = state.status == PlayerStatus.Loading || state.status == PlayerStatus.Buffering
    if (busy) {
        Pill(
            text = when {
                state.reauthorizing -> "Re-authorizing stream…"
                state.status == PlayerStatus.Loading -> "Loading…"
                else -> "Buffering…"
            },
            modifier = Modifier.align(Alignment.Center),
        )
    }
    if (state.rateLimited) {
        Pill("Server is rate limiting, retrying…", Modifier.align(Alignment.TopEnd).padding(32.dp), color = AppColors.Error)
    }
    if (state.status == PlayerStatus.Paused && !state.isLive) {
        Pill("Paused", Modifier.align(Alignment.TopStart).padding(32.dp))
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier = Modifier, color: Color = AppColors.Text) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.Background.copy(alpha = 0.8f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
private fun BoxScope.VodOverlay(state: PlayerUiState) {
    val session = state.session ?: return
    val (title, subtitle) = when (session) {
        is PlaybackSession.Movie -> session.item.displayTitle to listOfNotNull(session.item.displayYear, session.item.displayGenres).joinToString(" · ")
        is PlaybackSession.Episode -> session.series.displayTitle to buildString {
            // Season from the picked season (session.seasonNumber); episode number and title
            // parsed the same way as the episode row (EpisodeInfo), not episode.seriesNumber,
            // which is the portal's episode selector on Stalker.
            val ep = session.episode
            val index = session.siblings.indexOfFirst { it.id == ep.id }.coerceAtLeast(0)
            session.seasonNumber?.let { append("Season $it · ") }
            if (EpisodeInfo.hasOwnNumber(ep) || session.siblings.isNotEmpty()) append("E${EpisodeInfo.number(ep, index)} · ")
            append(EpisodeInfo.title(ep, index))
        }
        is PlaybackSession.Live -> session.channel.displayName to ""
    }
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = AppColors.Text, fontWeight = FontWeight.SemiBold)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted)
        // What the decoder is actually playing (shown with the overlay on play/pause).
        state.videoStats?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = AppColors.TextMuted) }
        Spacer(Modifier.height(16.dp))
        ProgressBar(
            fraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
            buffered = if (state.durationMs > 0) state.bufferedMs.toFloat() / state.durationMs else 0f,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(state.positionMs), style = MaterialTheme.typography.bodyMedium, color = AppColors.Text)
            Text(
                if (state.durationMs > 0) "-${formatTime(state.durationMs - state.positionMs)}  ·  ${formatTime(state.durationMs)}" else "",
                style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted,
            )
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, buffered: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(AppColors.SurfaceVariant)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(buffered.coerceIn(0f, 1f)).background(AppColors.TextMuted.copy(alpha = 0.5f)))
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(AppColors.Primary))
    }
}

@Composable
private fun BoxScope.LiveBanner(channel: Channel, pending: Boolean, programmes: Pair<EpgProgramme?, EpgProgramme?>?, videoStats: String? = null) {
    val now = System.currentTimeMillis()
    Column(
        Modifier
            .align(Alignment.TopStart)
            .padding(40.dp)
            .widthIn(max = 640.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Background.copy(alpha = 0.85f))
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            channel.number?.let {
                Text(it.toString(), style = MaterialTheme.typography.headlineLarge, color = AppColors.Primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(16.dp))
            }
            Text(channel.displayName, style = MaterialTheme.typography.headlineSmall, color = AppColors.Text, fontWeight = FontWeight.SemiBold)
        }
        videoStats?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = AppColors.TextMuted) }
        if (pending) {
            Text("Switching…", style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
        }
        val current = programmes?.first
        val next = programmes?.second
        if (current != null) {
            Spacer(Modifier.height(8.dp))
            Text(current.name ?: "", style = MaterialTheme.typography.bodyLarge, color = AppColors.Text)
            Spacer(Modifier.height(6.dp))
            ProgressBar(fraction = current.progressAt(now), buffered = 0f)
        }
        if (next != null) {
            Spacer(Modifier.height(6.dp))
            Text("Next: ${next.name ?: ""}", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
        }
    }
}

@Composable
private fun BoxScope.NextEpisodeButton(
    title: String,
    loading: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onLeave: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(48.dp)
            // Up returns to the video (Back already leaves the player as usual).
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) { onLeave(); true } else false
            }
            .onFocusChanged { focused = it.isFocused }
            .focusRequester(focusRequester),
    ) {
        Text(
            when {
                loading -> "Loading…"
                focused -> "Play next: $title"
                else -> "▼  Next episode: $title"
            },
        )
    }
}

@Composable
private fun BoxScope.ErrorPanel(message: String, retryFocus: FocusRequester, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .align(Alignment.Center)
            .widthIn(max = 720.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Playback problem", style = MaterialTheme.typography.headlineSmall, color = AppColors.Error, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = AppColors.Text)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) { Text("Retry") }
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

internal fun formatTime(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
