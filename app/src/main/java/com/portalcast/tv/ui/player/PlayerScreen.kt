@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.portalcast.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import com.portalcast.tv.appContainer
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
 * VOD, Jellyfin-style: Up or Down brings up the control row under the progress bar
 * (play/pause, ±10 s, next episode, subtitles CC, audio) with focus on play/pause;
 * CC and audio open a popup above the row. Up or Back from the row returns to the
 * video, and the row hides by itself after [CONTROLS_IDLE_MS] without a key while playing.
 * The remote's captions key opens the subtitles popup directly.
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
    val controls = remember { PlayerControlFocus() }
    var controlsFocused by remember { mutableStateOf(false) }
    var nextFocused by remember { mutableStateOf(false) }
    var controlsTick by remember { mutableIntStateOf(0) }
    // OK / play-pause on the remote: a big icon of the new state that grows and fades (Netflix).
    var flash by remember { mutableStateOf(Flash(playing = true, nonce = 0)) }
    // Focus to move once the target is composed (the row appears with the overlay).
    var focusTarget by remember { mutableStateOf<FocusRequester?>(null) }

    SideEffect { vm.liveChannelRefresher = refreshLiveChannel }

    // Settings › Player: another app plays it; its reported position is saved when it returns.
    val context = androidx.compose.ui.platform.LocalContext.current
    var externalSession by remember { mutableStateOf<com.portalcast.tv.playback.PlaybackSession?>(null) }
    val externalLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { r ->
        externalSession?.let { vm.finishExternal(it, com.portalcast.tv.playback.ExternalPlayer.result(r.data)) }
        onBack()
    }
    LaunchedEffect(Unit) {
        val session = PlaybackHolder.current
        if (session == null) { onBack(); return@LaunchedEffect }
        val choice = context.appContainer.localPrefs.externalPlayer.first()
        if (choice != com.portalcast.tv.playback.ExternalPlayer.BUILT_IN) {
            val prepared = vm.prepareExternal(session, PlaybackHolder.resumeFromMs)
            if (prepared != null) {
                val launched = runCatching {
                    externalSession = session
                    externalLauncher.launch(com.portalcast.tv.playback.ExternalPlayer.intent(prepared.first, session.label, prepared.second, choice))
                }.isSuccess
                if (launched) return@LaunchedEffect
                externalSession = null // the chosen app is gone: the built-in player instead
            }
        }
        vm.start(session, PlaybackHolder.resumeFromMs)
    }
    LaunchedEffect(Unit) { vm.navigateBack.collect { onBack() } }

    val exit = { vm.flushForExit(); onBack() }
    // Focus goes back to the video before the row hides: a hidden focused button leaves the remote dead.
    val leaveControls = { hide: Boolean ->
        runCatching { rootFocus.requestFocus() }
        if (hide) vm.hideOverlay()
    }
    val enterControls = {
        vm.showOverlay(autoHide = false)
        focusTarget = controls.playPause
    }
    BackHandler(onBack = exit)
    BackHandler(enabled = controlsFocused && state.subtitlePanel == null) { leaveControls(true) }
    BackHandler(enabled = state.subtitlePanel != null) { vm.closeSubtitlePanel() }

    LaunchedEffect(focusTarget) {
        val target = focusTarget ?: return@LaunchedEffect
        repeat(20) {
            withFrameNanos { }
            if (runCatching { target.requestFocus() }.isSuccess) { focusTarget = null; return@LaunchedEffect }
        }
        focusTarget = null
    }
    // While the row or a popup is in use the overlay stays up; idle for a while (playing) hides it.
    val popupOpen = state.subtitlePanel != null
    LaunchedEffect(controlsFocused, popupOpen, controlsTick) {
        vm.controlsActive = controlsFocused || popupOpen
        if (controlsFocused && !popupOpen) {
            delay(CONTROLS_IDLE_MS)
            if (vm.player.isPlaying) leaveControls(true)
        }
    }
    // A popup closed: focus returns to the button that opened it.
    var lastPopupAudio by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state.subtitlePanel?.audioOnly) {
        val now = state.subtitlePanel?.audioOnly
        val was = lastPopupAudio
        lastPopupAudio = now
        if (now == null && was != null) {
            vm.showOverlay(autoHide = false)
            focusTarget = if (was) controls.audio else controls.subtitles
        }
    }

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
                if (hasError || state.subtitlePanel != null || controlsFocused || nextFocused) return@onKeyEvent false
                val seekKey = event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                    event.key == Key.MediaRewind || event.key == Key.MediaFastForward
                // Let go after holding: one seek to where the bar was moved.
                if (event.type == KeyEventType.KeyUp) return@onKeyEvent seekKey && vm.scrubRelease()
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val live = state.isLive
                // A press jumps 10 s; held, the bar moves (faster, then steady) until released.
                val held = event.nativeKeyEvent.repeatCount > 0
                val step = PlayerViewModel.SEEK_STEP_MS
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                        if (!live) flash = Flash(playing = !(state.status == PlayerStatus.Playing || state.status == PlayerStatus.Buffering), nonce = flash.nonce + 1)
                        vm.togglePlayPause(); true
                    }
                    Key.MediaPlay -> { if (!live) flash = Flash(playing = true, nonce = flash.nonce + 1); vm.play(); true }
                    Key.MediaPause -> { if (!live) flash = Flash(playing = false, nonce = flash.nonce + 1); vm.pause(); true }
                    Key.MediaStop -> { exit(); true }
                    Key.DirectionLeft, Key.MediaRewind -> { if (live) vm.showOverlay() else if (held) vm.scrubHeld(forward = false) else vm.seekBy(-step); true }
                    Key.DirectionRight, Key.MediaFastForward -> { if (live) vm.showOverlay() else if (held) vm.scrubHeld(forward = true) else vm.seekBy(step); true }
                    // Movies/episodes: the bar has focus (Left/Right seek, OK pauses); Up goes to
                    // the buttons above it, as on Netflix.
                    Key.DirectionUp -> { if (live) vm.channelStep(-LIVE_DOWN_KEY_DELTA) else enterControls(); true }
                    Key.Captions -> { if (!live) vm.openSubtitlePanel(); true }
                    Key.DirectionDown -> {
                        when {
                            live -> vm.channelStep(LIVE_DOWN_KEY_DELTA)
                            // Near the end with the row hidden, Down picks the floating "Next episode".
                            state.showNextEpisode && !state.overlayVisible -> runCatching { nextFocus.requestFocus() }
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
                if (state.overlayVisible) {
                    VodOverlay(state, barFocused = !controlsFocused) {
                        PlayerControlRow(
                            state = state,
                            vm = vm,
                            focus = controls,
                            onFocusChanged = { controlsFocused = it },
                            onActivity = { controlsTick++ },
                            onLeave = { leaveControls(false); vm.showOverlay() },
                        )
                    }
                }
                // With the overlay up, "next episode" is a button in the control row.
                if (state.showNextEpisode && !state.overlayVisible) {
                    NextEpisodeButton(
                        title = state.nextEpisodeTitle ?: "Next episode",
                        loading = state.nextEpisodeLoading,
                        focusRequester = nextFocus,
                        onClick = vm::playNextEpisode,
                        onLeave = { runCatching { rootFocus.requestFocus() } },
                        onFocus = { nextFocused = it },
                    )
                }
            }
        }

        if (!hasError) StatusPills(state)
        if (!hasError) PlayPauseFlash(flash)
        state.subtitleText?.let { SubtitleLine(it) }
        state.subtitlePanel?.let { SubtitlePanel(it, state.subtitleDelayMs, state.externalSubtitles, vm) }

    }
}

/** The play/pause flash: the state it shows and a counter that restarts it on every press. */
private data class Flash(val playing: Boolean, val nonce: Int)

@Composable
private fun BoxScope.PlayPauseFlash(flash: Flash) {
    if (flash.nonce == 0) return
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(flash.nonce) {
        progress.snapTo(0f)
        progress.animateTo(1f, androidx.compose.animation.core.tween(650, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
    }
    val t = progress.value
    if (t >= 1f) return
    Box(
        Modifier
            .align(Alignment.Center)
            .size(112.dp)
            .graphicsLayer { val scale = 0.85f + 0.45f * t; scaleX = scale; scaleY = scale; alpha = 1f - t }
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.tv.material3.Icon(if (flash.playing) PlayerIcons.Play else PlayerIcons.Pause, contentDescription = null, modifier = Modifier.size(56.dp), tint = Color.White)
    }
}

/** DPAD_DOWN steps forward through the playlist (like a channel list); flip the sign to invert. */
private const val LIVE_DOWN_KEY_DELTA = 1

/** The control row hides after this long without a key while playing. */
private const val CONTROLS_IDLE_MS = 6_000L

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
private fun BoxScope.VodOverlay(state: PlayerUiState, barFocused: Boolean, controlRow: @Composable () -> Unit) {
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
    // While Left/Right is held the bar shows where the player will go, not where it is.
    val scrub = state.scrubMs
    val shownMs = scrub ?: state.positionMs
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            // Tall and soft, so the picture shows through above the text.
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.92f)))
            .padding(start = 56.dp, end = 56.dp, top = 72.dp, bottom = 16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = AppColors.Text, fontWeight = FontWeight.SemiBold)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted)
        // What the decoder is actually playing: small and quiet, for whoever wants it.
        state.videoStats?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = AppColors.TextMuted.copy(alpha = 0.7f), modifier = Modifier.padding(top = 2.dp)) }
        // Netflix's order: the buttons, then the bar, then the times under it.
        Spacer(Modifier.height(10.dp))
        controlRow()
        Spacer(Modifier.height(if (scrub != null) 40.dp else 8.dp))
        ProgressBar(
            fraction = if (state.durationMs > 0) shownMs.toFloat() / state.durationMs else 0f,
            buffered = if (state.durationMs > 0) state.bufferedMs.toFloat() / state.durationMs else 0f,
            focused = barFocused,
            // Where the bar is going and how far that is from where the picture is.
            scrubLabel = scrub?.let { "${formatTime(it)}  ·  ${if (it >= state.positionMs) "+" else "−"}${formatTime(kotlin.math.abs(it - state.positionMs))}" },
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(shownMs), style = MaterialTheme.typography.bodyMedium, color = AppColors.Text)
            Text(
                if (state.durationMs > 0) "-${formatTime(state.durationMs - shownMs)}  ·  ${formatTime(state.durationMs)}" else "",
                style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted,
            )
        }
    }
}

/**
 * The seek bar. With focus ([focused]: Left/Right seek, OK pauses) it is brighter, with a big
 * white handle ringed in the focus colour, like the focused buttons; held Left/Right grows it and
 * shows where it will land ([scrubLabel]). Without focus it is thin, with a small handle.
 */
@Composable
private fun ProgressBar(fraction: Float, buffered: Float, focused: Boolean = false, scrubLabel: String? = null) {
    val f = fraction.coerceIn(0f, 1f)
    val barHeight = if (scrubLabel != null) 8.dp else if (focused) 6.dp else 3.dp
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterStart) {
        val x = maxWidth * f
        Box(Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(barHeight / 2)).background(Color.White.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(buffered.coerceIn(0f, 1f)).background(Color.White.copy(alpha = 0.35f)))
            Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(AppColors.Primary))
        }
        val knob = if (scrubLabel != null) 22.dp else if (focused) 20.dp else 10.dp
        Box(
            Modifier
                .offset(x = (x - knob / 2).coerceAtLeast(0.dp))
                .size(knob)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.White)
                .then(if (focused || scrubLabel != null) Modifier.border(3.dp, AppColors.Focus, androidx.compose.foundation.shape.CircleShape) else Modifier),
        )
        if (scrubLabel != null) {
            // The bubble follows the handle and stays on screen at either end.
            val bubbleWidth = 220.dp
            Box(
                Modifier
                    .offset(x = (x - bubbleWidth / 2).coerceIn(0.dp, (maxWidth - bubbleWidth).coerceAtLeast(0.dp)), y = (-34).dp)
                    .width(bubbleWidth),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    scrubLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = AppColors.Text,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
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
    onFocus: (Boolean) -> Unit,
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
            .onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) }
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
