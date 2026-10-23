package com.portalcast.tv.ui.sports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.SportsGame
import com.portalcast.tv.data.model.SportsResponse
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.repo.LiveRepository
import com.portalcast.tv.playback.PlaybackHolder
import com.portalcast.tv.playback.PlaybackSession
import com.portalcast.tv.ui.components.ErrorPanel
import com.portalcast.tv.ui.components.LoadingPanel
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.components.TvButtonStyle
import com.portalcast.tv.ui.theme.AppColors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class SportsUiState(
    val loading: Boolean = true,
    val error: Throwable? = null,
    val answer: SportsResponse? = null,
    /** The viewer's channels by id, for names and for playing a matched channel. */
    val channelsById: Map<String, Channel> = emptyMap(),
    /** The chosen sport chip; null = all. */
    val sport: String? = null,
    /** A game with several matched channels, while its picker is open. */
    val picking: SportsGame? = null,
    /** "Choose sports" while open: every league and the ticked ones. */
    val choosing: Chooser? = null,
) {
    data class Chooser(
        val leagues: List<com.portalcast.tv.data.model.SportsLeagueInfo> = emptyList(),
        val chosen: List<String> = emptyList(),
        val max: Int = 20,
        val loading: Boolean = true,
        val saving: Boolean = false,
        val error: String? = null,
    )
}

/**
 * `GET /api/v2/sports` plus the cached channel list. While the server's first fetch is still
 * running (`loading`), asks again a few times. Reloaded each time the tab shows.
 */
class SportsViewModel(private val live: LiveRepository, private val users: com.portalcast.tv.data.repo.UserRepository) : ViewModel() {
    private val _state = MutableStateFlow(SportsUiState())
    val state: StateFlow<SportsUiState> = _state.asStateFlow()
    var lastFocusedGameId: String? = null

    fun load(fresh: Boolean = false) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val (sports, channels) = coroutineScope {
                val s = async { live.sports(DAYS, fresh) }
                val c = async { live.channels() }
                s.await() to c.await()
            }
            val byId = channels.getOrNull()?.associateBy { it.id } ?: _state.value.channelsById
            sports.fold(
                { answer ->
                    _state.update { s -> s.copy(loading = false, answer = answer, channelsById = byId, sport = s.sport?.takeIf { sp -> sp in SportsLayout.sportsOf(answer.games) || answer.leagues.any { it.sport == sp } }) }
                    if (answer.loading && retries++ < 3) { delay(4000); load(fresh = true) }
                },
                // A failed refresh keeps what is shown.
                { e -> _state.update { it.copy(loading = false, error = e.takeIf { _ -> it.answer == null }) } },
            )
        }
    }

    /** Asks again while the server's first fetch runs, at most 3 times per screen. */
    private var retries = 0

    fun selectSport(sport: String?) = _state.update { it.copy(sport = sport) }

    /** Plays the game's channel, or asks which one when the guide shows it on several. */
    fun watch(game: SportsGame): Boolean {
        val channels = SportsLayout.channelsOf(game, _state.value.channelsById)
        lastFocusedGameId = game.id
        return when (channels.size) {
            0 -> false
            1 -> { play(channels[0], channels); true }
            else -> { _state.update { it.copy(picking = game) }; false }
        }
    }

    fun play(channel: Channel, playlist: List<Channel>) {
        _state.update { it.copy(picking = null) }
        PlaybackHolder.start(PlaybackSession.Live(channel, playlist))
        viewModelScope.launch { live.recordRecent(channel.id, channel.groupId) }
    }

    fun dismissPicker() = _state.update { it.copy(picking = null) }

    // ---- Choose sports: every league, saved in the viewer's preferences (all devices) ----
    fun openChooser() {
        _state.update { it.copy(choosing = SportsUiState.Chooser()) }
        viewModelScope.launch {
            live.sportsLeagues().fold(
                { l -> _state.update { it.copy(choosing = it.choosing?.copy(leagues = l.leagues, chosen = l.chosen, max = l.max, loading = false)) } },
                { e -> _state.update { it.copy(choosing = it.choosing?.copy(loading = false, error = e.message ?: "Couldn't load the list.")) } },
            )
        }
    }

    fun toggleLeague(id: String) = _state.update { s ->
        val c = s.choosing ?: return@update s
        val next = if (id in c.chosen) c.chosen - id else if (c.chosen.size >= c.max) c.chosen else c.chosen + id
        s.copy(choosing = c.copy(chosen = next))
    }

    fun closeChooser() = _state.update { it.copy(choosing = null) }

    fun saveChooser() {
        val c = _state.value.choosing ?: return
        if (c.chosen.isEmpty()) return
        _state.update { it.copy(choosing = c.copy(saving = true, error = null)) }
        viewModelScope.launch {
            users.updatePreferences(com.portalcast.tv.data.model.Preferences(sportsLeagues = c.chosen)).fold(
                { _state.update { it.copy(choosing = null, sport = null) }; retries = 0; load(fresh = true) },
                { e -> _state.update { it.copy(choosing = it.choosing?.copy(saving = false, error = "Couldn't save: ${e.message ?: "try again"}")) } },
            )
        }
    }

    // A month: some sports (cricket) have a game every few weeks in the source.
    companion object { const val DAYS = 30 }
}

/**
 * The Sports tab (plan Phase 6.7): games of the viewer's leagues (Choose sports; none chosen:
 * the admin's) for the next 30 days by day, a
 * chip per sport. A game the guide shows plays its channel; otherwise "Find channel" searches
 * Live TV for the broadcaster the schedule names. Credit: "Schedule: sporting-events.org".
 */
@Composable
fun SportsScreen(
    onPlay: () -> Unit,
    onFindChannel: (String) -> Unit,
    restoreFocus: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: SportsViewModel = viewModel(key = "sports", factory = viewModelFactory { initializer { SportsViewModel(container.liveRepository, container.userRepository) } })
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    val answer = state.answer
    if (answer == null) {
        if (state.loading) LoadingPanel(modifier, message = "Loading the schedule…")
        else ErrorPanel(sportsErrorMessage(state.error), onRetry = vm::load, modifier = modifier, requestFocus = true)
        return
    }
    val zone = remember { ZoneId.systemDefault() }
    val use24h = remember { android.text.format.DateFormat.is24HourFormat(context) }
    // A chip per chosen league's sport, even one with no game this month (its next games show).
    val sports = remember(answer) { (answer.leagues.map { it.sport }.filter { it.isNotBlank() } + SportsLayout.sportsOf(answer.games)).distinct() }
    val days = remember(answer, state.sport) { SportsLayout.days(answer.games, state.sport, zone, LocalDate.now(zone)) }

    state.choosing?.let { c ->
        ChooseSportsPanel(c, onToggle = vm::toggleLeague, onSave = vm::saveChooser, onDismiss = vm::closeChooser)
    }
    state.picking?.let { game ->
        val channels = SportsLayout.channelsOf(game, state.channelsById)
        ChannelPicker(game, channels, onPick = { vm.play(it, channels); onPlay() }, onDismiss = vm::dismissPicker)
    }

    Column(modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 24.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sports", style = MaterialTheme.typography.headlineSmall, color = AppColors.Text)
            val count = days.sumOf { it.games.size }
            Text("$count ${if (count == 1) "game" else "games"} in the next 30 days", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
            Spacer(Modifier.weight(1f))
            Text("Schedule: ${answer.source.name}", style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            // Room at both ends: a focused chip grows and its ring was cut at the left edge.
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 6.dp),
        ) {
            item(key = "choose") { SportChip("⚙  Choose sports", selected = false, onClick = vm::openChooser) }
            if (sports.size > 1) {
                item(key = "all") { SportChip("All", state.sport == null) { vm.selectSport(null) } }
                items(sports, key = { it }) { s -> SportChip(s, state.sport == s) { vm.selectSport(s) } }
            }
        }
        if (days.isEmpty()) {
            Text(
                if (answer.loading) "Fetching the schedule…" else "No games in the next 30 days for your leagues. Choose sports to add more.",
                color = AppColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 24.dp),
            )
            return@Column
        }
        val restoreRequester = remember { FocusRequester() }
        val restoreId = remember { if (restoreFocus) vm.lastFocusedGameId else null }
        LaunchedEffect(restoreId, days) {
            if (restoreId == null) return@LaunchedEffect
            repeat(10) { withFrameNanos { }; if (runCatching { restoreRequester.requestFocus() }.isSuccess) return@LaunchedEffect }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            for (day in days) {
                item(key = "day:${day.date}") {
                    Text(
                        day.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.Text,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                }
                items(day.games, key = { "game:${day.date}:${it.id}" }) { game ->
                    val channels = SportsLayout.channelsOf(game, state.channelsById)
                    GameRow(
                        game = game,
                        time = SportsLayout.timeLabel(game, zone, use24h),
                        channels = channels,
                        onClick = {
                            if (channels.isNotEmpty()) {
                                if (vm.watch(game)) onPlay()
                            } else {
                                vm.lastFocusedGameId = game.id
                                onFindChannel(SportsLayout.findQuery(game) ?: game.title)
                            }
                        },
                        modifier = if (game.id == restoreId) Modifier.focusRequester(restoreRequester) else Modifier,
                    )
                }
            }
        }
    }
}

private fun sportsErrorMessage(e: Throwable?): String = when {
    e is UnsupportedOperationException || (e is ApiError.Http && e.code == 404) ->
        "This server has no Sports page yet. Update the PortalCast server to get it."
    e != null -> e.message ?: "Couldn't load the schedule."
    else -> "Couldn't load the schedule."
}

@Composable
private fun SportChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, AppColors.Focus), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) AppColors.Primary else AppColors.SurfaceVariant,
            contentColor = if (selected) AppColors.OnPrimary else AppColors.Text,
            focusedContainerColor = if (selected) AppColors.Primary else AppColors.GlassFillFocused,
            focusedContentColor = if (selected) AppColors.OnPrimary else AppColors.Text,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/**
 * One game: time (or LIVE) on the left, the fixture and "League · City" in the middle, and on
 * the right the channel the guide shows it on, or the broadcasters with "Find channel".
 */
@Composable
private fun GameRow(game: SportsGame, time: String, channels: List<Channel>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, AppColors.Focus), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.Surface.copy(alpha = 0.85f),
            contentColor = AppColors.Text,
            focusedContainerColor = AppColors.SurfaceVariant,
            focusedContentColor = AppColors.Text,
        ),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(120.dp)) {
                Text(time, style = MaterialTheme.typography.titleMedium, color = if (game.start == null) AppColors.TextMuted else AppColors.Text)
                if (game.live) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.background(Color(0xFFDC2626), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(game.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = SportsLayout.metaLine(game)
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.widthIn(max = 360.dp), horizontalAlignment = Alignment.End) {
                if (channels.isNotEmpty()) {
                    Text(
                        "▶ ${channels[0].displayName}" + if (channels.size > 1) "  +${channels.size - 1}" else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = AppColors.Primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    if (game.broadcast.isNotEmpty()) {
                        Text(game.broadcast.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("Find channel", style = MaterialTheme.typography.labelMedium, color = AppColors.Text)
                }
            }
        }
    }
}

/** The game is on several channels: pick one (they also become the channel up/down list). */
@Composable
private fun ChannelPicker(game: SportsGame, channels: List<Channel>, onPick: (Channel) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { repeat(10) { withFrameNanos { }; if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect } }
        Box(Modifier.background(AppColors.Surface, RoundedCornerShape(16.dp)).padding(24.dp).widthIn(min = 360.dp, max = 560.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(game.title, style = MaterialTheme.typography.titleLarge, color = AppColors.Text)
                Text("The guide shows it on", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
                channels.forEachIndexed { i, ch ->
                    TvButton(
                        text = ch.displayName,
                        onClick = { onPick(ch) },
                        modifier = (if (i == 0) Modifier.focusRequester(first) else Modifier).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Choose sports: every league by sport, the viewer's ticked (at most 20), full screen. Center
 * ticks; Save shows them; Back leaves without saving.
 */
@Composable
private fun ChooseSportsPanel(c: SportsUiState.Chooser, onToggle: (String) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        val first = remember { FocusRequester() }
        LaunchedEffect(c.loading) { if (!c.loading) repeat(10) { withFrameNanos { }; if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect } }
        Column(Modifier.fillMaxSize().background(AppColors.Background).padding(horizontal = 48.dp, vertical = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Choose sports", style = MaterialTheme.typography.headlineSmall, color = AppColors.Text)
                Text("${c.chosen.size} / ${c.max}", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
                Spacer(Modifier.weight(1f))
                TvButton(
                    text = if (c.saving) "Saving…" else "Save",
                    onClick = onSave,
                    enabled = !c.saving && c.chosen.isNotEmpty(),
                    style = com.portalcast.tv.ui.components.TvButtonStyle.Primary,
                )
                TvButton(text = "Cancel", onClick = onDismiss)
            }
            c.error?.let { Text(it, color = AppColors.Error, modifier = Modifier.padding(top = 8.dp)) }
            if (c.loading) {
                LoadingPanel(Modifier.fillMaxWidth().weight(1f))
                return@Column
            }
            val bySport = c.leagues.groupBy { it.sport }.toList()
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                var firstDone = false
                for ((sport, leagues) in bySport) {
                    item(key = "h:$sport", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(sport, style = MaterialTheme.typography.titleMedium, color = AppColors.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                    }
                    for (l in leagues) {
                        val isFirst = !firstDone
                        firstDone = true
                        item(key = l.id) {
                            val on = l.id in c.chosen
                            TvButton(
                                text = (if (on) "✓  " else "") + l.name,
                                onClick = { onToggle(l.id) },
                                style = if (on) com.portalcast.tv.ui.components.TvButtonStyle.Primary else TvButtonStyle.Subtle,
                                modifier = (if (isFirst) Modifier.focusRequester(first) else Modifier).fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
