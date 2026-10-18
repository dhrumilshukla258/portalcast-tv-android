package com.portalcast.tv.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.ChannelGroup
import com.portalcast.tv.ui.components.ChannelCard
import com.portalcast.tv.ui.components.EmptyPanel
import com.portalcast.tv.ui.components.ErrorPanel
import com.portalcast.tv.ui.components.LoadingPanel
import com.portalcast.tv.ui.components.SideListItem
import com.portalcast.tv.ui.theme.AppColors
import kotlinx.coroutines.delay

private const val EPG_REFRESH_MS = 30 * 60 * 1000L
private const val CLOCK_TICK_MS = 30_000L

/**
 * Live TV tab: group column (Recent, Favourites, All, server groups) and a channel grid
 * with now-playing from the EPG. Center plays; long-press Center or Menu toggles favourite.
 *
 * @param onPlay called after `PlaybackHolder.start`; navigate to the player.
 * @param restoreFocus true when Home is being re-entered (back from the player): focus the
 *   last played/focused channel instead of leaving focus to the rail.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveScreen(onPlay: () -> Unit, modifier: Modifier = Modifier, restoreFocus: Boolean = false) {
    val container = LocalContext.current.appContainer
    val vm: LiveViewModel = viewModel(
        factory = viewModelFactory { initializer { LiveViewModel(container.liveRepository, container.userRepository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    // EPG refresh while the tab is on screen; the clock drives the programme progress bars.
    LaunchedEffect(Unit) {
        while (true) {
            delay(EPG_REFRESH_MS)
            vm.refreshEpg(forceRefresh = true)
        }
    }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(CLOCK_TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            delay(4000)
            vm.clearNotice()
        }
    }

    val restoreRequester = remember { FocusRequester() }
    LaunchedEffect(restoreFocus, state.visible.isNotEmpty()) {
        if (restoreFocus && state.visible.isNotEmpty()) {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { restoreRequester.requestFocus() }
        }
    }

    Row(modifier.fillMaxSize().padding(top = 32.dp)) {
        LazyColumn(
            modifier = Modifier.width(240.dp).fillMaxHeight().focusRestorer(),
            contentPadding = PaddingValues(start = 24.dp, end = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(state.groups, key = { i, g -> "$i:${g.id}" }) { _, group ->
                SideListItem(
                    text = group.displayTitle,
                    selected = group.id == state.selectedGroupId,
                    onClick = { vm.selectGroup(group.id) },
                )
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.padding(start = 16.dp, end = 32.dp, bottom = 8.dp)) {
                Text(
                    state.selectedGroup?.displayTitle ?: "Live TV",
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppColors.Text,
                    modifier = Modifier.weight(1f),
                )
                val notice = state.notice
                if (notice != null) {
                    Text(notice, style = MaterialTheme.typography.bodyMedium, color = AppColors.Error)
                } else if (!state.loading && state.error == null) {
                    Text("${state.visible.size} channels", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
                }
            }
            Box(Modifier.fillMaxSize()) {
                val error = state.error
                when {
                    state.loading && state.channels.isEmpty() -> LoadingPanel()
                    error != null && state.channels.isEmpty() -> ErrorPanel(error, onRetry = { vm.load(forceRefresh = true) })
                    state.visible.isEmpty() -> EmptyPanel(emptyMessage(state.selectedGroupId))
                    else -> {
                        val gridState = rememberLazyGridState()
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = gridState,
                            modifier = Modifier.fillMaxSize().focusRestorer(),
                            contentPadding = PaddingValues(start = 16.dp, end = 32.dp, top = 8.dp, bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            itemsIndexed(state.visible, key = { i, c -> "$i:${c.id}" }) { _, channel ->
                                val (current, _) = state.epg?.nowNext(channel.id, now) ?: (null to null)
                                ChannelCard(
                                    channel = channel,
                                    onClick = {
                                        vm.play(channel)
                                        onPlay()
                                    },
                                    onLongClick = { vm.toggleFavorite(channel) },
                                    nowPlaying = current?.name,
                                    nowProgress = current?.progressAt(now),
                                    isFavorite = channel.id in state.favorites,
                                    modifier = Modifier
                                        .onFocusChanged { if (it.hasFocus) vm.lastFocusedChannelId = channel.id }
                                        .then(if (channel.id == vm.lastFocusedChannelId) Modifier.focusRequester(restoreRequester) else Modifier),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun emptyMessage(groupId: String): String = when (groupId) {
    ChannelGroup.FAVORITES_ID -> "No favourites yet. Long-press Center (or press Menu) on a channel to add it."
    ChannelGroup.RECENT_ID -> "Channels you watch will show up here."
    else -> "No channels in this group."
}
