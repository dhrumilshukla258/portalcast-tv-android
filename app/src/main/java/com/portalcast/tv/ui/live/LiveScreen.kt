package com.portalcast.tv.ui.live

import com.portalcast.tv.ui.components.rememberedIn
import com.portalcast.tv.ui.components.rememberFocusMemory
import com.portalcast.tv.ui.components.focusMemory
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.ChannelGroup
import com.portalcast.tv.ui.components.EmptyPanel
import com.portalcast.tv.ui.components.ErrorPanel
import com.portalcast.tv.ui.components.LoadingPanel
import com.portalcast.tv.ui.components.SideListItem
import com.portalcast.tv.ui.components.rememberServerUrl
import com.portalcast.tv.ui.theme.AppColors
import kotlinx.coroutines.delay

private const val EPG_REFRESH_MS = 30 * 60 * 1000L
private const val CLOCK_TICK_MS = 30_000L

/**
 * Live TV tab, laid out like the webui's channel list: group column (Recent, Favourites,
 * All, server groups; Center selects), a vertical channel list with now/next from the EPG,
 * and a details panel for the focused channel. Center plays; long-press Center or Menu
 * toggles favourite. Left from the list goes to the groups, Left from the groups to the
 * rail; the details panel is not focusable.
 *
 * Performance (10k+ channels on a 32-bit TV): the ViewModel sorts, dedupes, filters and
 * indexes the EPG; rows are keyed by channel id and get only stable params, and the focused
 * channel lives in its own state read by [ChannelDetails] alone.
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
        factory = viewModelFactory { initializer { LiveViewModel(container.liveRepository, container.userRepository, container.pageStore.updates) } },
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

    val context = LocalContext.current
    val use24h = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val serverUrl = rememberServerUrl()
    val epg = remember(state.epgIndex) { EpgLookup(state.epgIndex) }
    val groupTitles = remember(state.groups) { state.groups.associate { it.id to it.displayTitle } }

    // Focus is hoisted here but read only by the details panel: writing it never recomposes the list.
    val focusedChannel = remember { mutableStateOf<Channel?>(null) }
    // One instance each for the whole screen, so rows see equal params and skip recomposition.
    val playChannel: (Channel) -> Unit = remember(vm) { { c -> vm.play(c); onPlay() } }
    val toggleFavorite: (Channel) -> Unit = remember(vm) { { c -> vm.toggleFavorite(c) } }
    val channelFocused: (Channel) -> Unit = remember(vm) { { c -> vm.lastFocusedChannelId = c.id; focusedChannel.value = c } }

    // Which row carries the restore requester: fixed per group (and once the list has loaded,
    // which is when the saved last channel becomes known), not whatever was focused last frame.
    val restoreId = remember(state.selectedGroupId, state.channels.isNotEmpty()) { vm.lastFocusedChannelId }
    val restoreRequester = remember { FocusRequester() }
    LaunchedEffect(restoreFocus, state.visible.isNotEmpty()) {
        if (restoreFocus && state.visible.isNotEmpty()) {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { restoreRequester.requestFocus() }
        }
    }

    val groupsMemory = rememberFocusMemory()
    Row(modifier.fillMaxSize().padding(top = 32.dp)) {
        LazyColumn(
            modifier = Modifier.width(210.dp).fillMaxHeight().focusMemory(groupsMemory),
            contentPadding = PaddingValues(start = 20.dp, end = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(state.groups, key = { i, g -> "$i:${g.id}" }) { _, group ->
                SideListItem(
                    text = group.displayTitle,
                    selected = group.id == state.selectedGroupId,
                    onClick = { vm.selectGroup(group.id) },
                    modifier = Modifier.rememberedIn(groupsMemory),
                )
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
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
                        // New list state per group, starting near the last focused channel so the
                        // restore target is composed (indexOf runs once per group, not per frame).
                        val listState = remember(state.selectedGroupId) {
                            val idx = state.visible.indexOfFirst { it.id == restoreId }
                            LazyListState(firstVisibleItemIndex = (idx - 2).coerceAtLeast(0))
                        }
                        val memory = rememberFocusMemory()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().focusMemory(memory),
                            contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(state.visible, key = { it.id }, contentType = { "channel" }) { channel ->
                                ChannelRow(
                                    channel = channel,
                                    epg = epg,
                                    nowMs = now,
                                    use24h = use24h,
                                    serverUrl = serverUrl,
                                    isFavorite = channel.id in state.favorites,
                                    onPlay = playChannel,
                                    onToggleFavorite = toggleFavorite,
                                    onFocused = channelFocused,
                                    modifier = (if (channel.id == restoreId) Modifier.focusRequester(restoreRequester) else Modifier).rememberedIn(memory),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Informational only: nothing in it is focusable, so Right from the list goes nowhere.
        ChannelDetails(
            focused = focusedChannel,
            epg = epg,
            groupTitles = groupTitles,
            favorites = state.favorites,
            nowMs = now,
            use24h = use24h,
            serverUrl = serverUrl,
            modifier = Modifier.width(300.dp).fillMaxHeight().padding(end = 24.dp, bottom = 24.dp),
        )
    }
}

private fun emptyMessage(groupId: String): String = when (groupId) {
    ChannelGroup.FAVORITES_ID -> "No favourites yet. Long-press Center (or press Menu) on a channel to add it."
    ChannelGroup.RECENT_ID -> "Channels you watch will show up here."
    else -> "No channels in this group."
}
