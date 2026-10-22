package com.portalcast.tv.ui.mylist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.repo.DiscoverRepository
import com.portalcast.tv.data.repo.LiveRepository
import com.portalcast.tv.playback.PlaybackHolder
import com.portalcast.tv.playback.PlaybackSession
import com.portalcast.tv.ui.components.ErrorPanel
import com.portalcast.tv.ui.components.LoadingPanel
import com.portalcast.tv.ui.components.LocalTitleMenu
import com.portalcast.tv.ui.components.MediaCard
import com.portalcast.tv.ui.components.TitleChanges
import com.portalcast.tv.ui.components.TitleMenuTarget
import com.portalcast.tv.ui.components.gridItem
import com.portalcast.tv.ui.components.keepColumn
import com.portalcast.tv.ui.components.rememberGridFocus
import com.portalcast.tv.ui.components.revealWithTitle
import com.portalcast.tv.ui.discover.DiscoverOpener
import com.portalcast.tv.ui.discover.OpenerEffects
import com.portalcast.tv.ui.live.ChannelRow
import com.portalcast.tv.ui.live.EpgLookup
import com.portalcast.tv.ui.theme.AppColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** My List's sections (owner, 2026-10-22: "access it in My List, so they can undo"). */
enum class MyListSection(val label: String) { LIST("My List"), WATCHED("Watched"), HIDDEN("Not interested"), CHANNELS("Favourite channels") }

data class MyListUiState(
    val section: MyListSection = MyListSection.LIST,
    val loading: Boolean = true,
    val error: Throwable? = null,
    /** Null until the first answer for the section. */
    val items: List<MediaItem>? = null,
    val channels: List<Channel>? = null,
)

/**
 * My List: the saved titles, the ones watched or marked watched, the Not interested ones and
 * the favourite channels. Undo is in the title menu (hold Center): Not watched, Show on Home
 * again; a favourite channel's star by holding Center on it. Read each time a section shows.
 */
class MyListViewModel(private val discover: DiscoverRepository, private val live: LiveRepository) : ViewModel() {
    private val _state = MutableStateFlow(MyListUiState())
    val state: StateFlow<MyListUiState> = _state.asStateFlow()
    val opener = DiscoverOpener(discover, viewModelScope)
    var lastFocusedIndex = 0

    init {
        // An undo from the title menu: the section is read again.
        viewModelScope.launch { TitleChanges.events.collect { load() } }
    }

    fun select(section: MyListSection) {
        if (section == _state.value.section) return
        lastFocusedIndex = 0
        _state.update { MyListUiState(section = section) }
        load()
    }

    fun load() {
        val section = _state.value.section
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            if (section == MyListSection.CHANNELS) {
                val favs = live.favorites().getOrNull().orEmpty().toSet()
                live.channels().fold(
                    { all -> _state.update { it.copy(loading = false, channels = all.filter { c -> c.id in favs }) } },
                    { e -> _state.update { it.copy(loading = false, error = e.takeIf { _ -> it.channels == null }) } },
                )
                return@launch
            }
            val result = when (section) {
                MyListSection.WATCHED -> discover.watchedTitles()
                MyListSection.HIDDEN -> discover.hiddenTitles()
                else -> discover.myList()
            }
            if (_state.value.section != section) return@launch
            result.fold(
                { list -> _state.update { it.copy(loading = false, items = list) } },
                // A failed refresh keeps what is shown.
                { e -> _state.update { it.copy(loading = false, error = e.takeIf { _ -> it.items == null }) } },
            )
        }
    }

    /** Remove a channel's star (hold Center on it). */
    fun unstar(channel: Channel) {
        _state.update { it.copy(channels = it.channels?.filterNot { c -> c.id == channel.id }) }
        viewModelScope.launch { if (live.toggleFavorite(channel.id).isFailure) load() }
    }

    fun play(channel: Channel) {
        val list = _state.value.channels.orEmpty()
        PlaybackHolder.start(PlaybackSession.Live(channel, list.ifEmpty { listOf(channel) }))
        viewModelScope.launch { live.recordRecent(channel.id, channel.groupId) }
    }
}

private const val COLUMNS = 6

@Composable
fun MyListScreen(
    onOpenMovie: (MediaItem) -> Unit,
    onOpenSeries: (MediaItem) -> Unit,
    restoreFocus: Boolean = false,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit = {},
) {
    val container = LocalContext.current.appContainer
    val vm: MyListViewModel = viewModel(key = "my-list", factory = viewModelFactory { initializer { MyListViewModel(container.discoverRepository, container.liveRepository) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val serverUrl by container.sessionStore.serverUrl.collectAsStateWithLifecycle()
    val opening by vm.opener.state.collectAsStateWithLifecycle()
    OpenerEffects(vm.opener, serverUrl, onOpenMovie, onOpenSeries)
    LaunchedEffect(Unit) { vm.load() }
    val titleMenu = LocalTitleMenu.current

    Column(modifier.fillMaxSize().padding(start = 48.dp, end = 32.dp, top = 20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("My List", style = MaterialTheme.typography.headlineSmall, color = AppColors.Text, modifier = Modifier.padding(end = 14.dp))
            for (s in MyListSection.entries) SectionChip(s.label, s == state.section) { vm.select(s) }
        }
        val hint = when (state.section) {
            MyListSection.WATCHED -> "Hold OK on a title for Not watched."
            MyListSection.HIDDEN -> "Hold OK on a title for Show on Home again."
            MyListSection.CHANNELS -> "Hold OK on a channel to remove its star."
            else -> null
        }
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted, modifier = Modifier.padding(top = 8.dp)) }
        opening.openError?.let { Text(it, color = AppColors.Error, modifier = Modifier.padding(top = 8.dp)) }

        if (state.section == MyListSection.CHANNELS) {
            val channels = state.channels
            when {
                channels == null && state.loading -> LoadingPanel()
                channels == null -> ErrorPanel(state.error ?: IllegalStateException("Unavailable"), onRetry = vm::load)
                channels.isEmpty() -> Empty("Star channels in Live TV (hold OK on one) to keep them here.")
                else -> LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(channels, key = { it.id }) { ch ->
                        ChannelRow(
                            channel = ch, epg = EpgLookup.Empty, nowMs = System.currentTimeMillis(), use24h = true, serverUrl = serverUrl,
                            isFavorite = true, onPlay = { vm.play(it); onPlay() }, onToggleFavorite = vm::unstar, onFocused = {},
                        )
                    }
                }
            }
            return@Column
        }

        val items = state.items
        when {
            items == null && state.loading -> { LoadingPanel(); return@Column }
            items == null -> { ErrorPanel(state.error ?: IllegalStateException("My List is unavailable"), onRetry = vm::load, requestFocus = true); return@Column }
            items.isEmpty() -> {
                Empty(
                    when (state.section) {
                        MyListSection.WATCHED -> "Titles you finish, or mark as watched (hold OK on a poster), show here."
                        MyListSection.HIDDEN -> "Titles you mark Not interested (hold OK on a poster) show here, so you can bring them back."
                        else -> "Nothing here yet. Choose \"+ My List\" on a movie or series, or hold OK on its poster."
                    },
                )
                return@Column
            }
        }
        val list = items!!
        val firstFocus = remember { FocusRequester() }
        // Back from a title opened here: focus its card. Otherwise focus stays where it is (on the tabs).
        LaunchedEffect(list) { if (restoreFocus) repeat(10) { withFrameNanos { }; if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect } }
        val gridState = rememberLazyGridState()
        val gridFocus = rememberGridFocus(gridState)
        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            state = gridState,
            modifier = Modifier.fillMaxSize().keepColumn(gridFocus, list.size),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(list, key = { _, it -> it.id }) { i, item ->
                MediaCard(
                    item = item,
                    onClick = { vm.lastFocusedIndex = i; vm.opener.open(item) },
                    width = Dp.Unspecified,
                    // Watched: the menu offers Not watched (undo).
                    onLongClick = titleMenu?.let { open -> { open(TitleMenuTarget(item, watched = state.section == MyListSection.WATCHED)) } },
                    modifier = (if (i == vm.lastFocusedIndex.coerceAtMost(list.lastIndex)) Modifier.focusRequester(firstFocus) else Modifier)
                        .gridItem(gridFocus, i)
                        .revealWithTitle(),
                )
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, color = AppColors.TextMuted, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 24.dp))
}

@Composable
private fun SectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, AppColors.Focus), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) androidx.compose.ui.graphics.Color.White else AppColors.SurfaceVariant,
            contentColor = if (selected) AppColors.Background else AppColors.Text,
            focusedContainerColor = if (selected) androidx.compose.ui.graphics.Color.White else AppColors.GlassFillFocused,
            focusedContentColor = if (selected) AppColors.Background else AppColors.Text,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}
