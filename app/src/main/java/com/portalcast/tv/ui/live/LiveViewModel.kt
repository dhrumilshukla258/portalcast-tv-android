package com.portalcast.tv.ui.live

import kotlinx.coroutines.flow.SharedFlow
import com.portalcast.tv.data.repo.DefaultLiveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.ChannelGroup
import com.portalcast.tv.data.model.EpgData
import com.portalcast.tv.data.model.EpgProgramme
import com.portalcast.tv.data.repo.DefaultUserRepository
import com.portalcast.tv.data.repo.LiveRepository
import com.portalcast.tv.data.repo.UserRepository
import com.portalcast.tv.playback.PlaybackHolder
import com.portalcast.tv.playback.PlaybackSession
import com.portalcast.tv.ui.components.panelMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Live tab: groups (synthetic Recent/Favourites/All + server), the whole channel list
 * filtered client-side, favourites/recents from preferences, EPG for now/next.
 */
class LiveViewModel(
    private val live: LiveRepository,
    private val users: UserRepository,
    /** Stored answers that changed after being shown (DiskCache.updates): the list reloads in place. */
    cacheUpdates: SharedFlow<String>? = null,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val error: Throwable? = null,
        val groups: List<ChannelGroup> = LiveFilter.withSyntheticGroups(emptyList()),
        val selectedGroupId: String = ChannelGroup.ALL_ID,
        val channels: List<Channel> = emptyList(),
        val favorites: Set<String> = emptySet(),
        val recents: List<String> = emptyList(),
        val visible: List<Channel> = emptyList(),
        val epg: EpgData? = null,
        /** [epg] by channel id, each list sorted by start ([LiveEpg.index]); rows look up O(1). */
        val epgIndex: Map<String, List<EpgProgramme>> = emptyMap(),
        /** Transient notice (favourite toggle failed). */
        val notice: String? = null,
    ) {
        val selectedGroup: ChannelGroup? get() = groups.firstOrNull { it.id == selectedGroupId }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** Last focused channel id, for focus restoration when coming back from the player. */
    var lastFocusedChannelId: String? = null

    init {
        load()
        cacheUpdates?.let { flow ->
            viewModelScope.launch { flow.collect { ns -> if (ns == DefaultLiveRepository.GROUPS_NS || ns == DefaultLiveRepository.CHANNELS_NS) load() } }
        }
    }

    fun load(forceRefresh: Boolean = false) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val (channelsResult, groupsResult) = coroutineScope {
                val c = async { live.channels(forceRefresh) }
                val g = async { live.groups(forceRefresh) }
                c.await() to g.await()
            }
            val unsorted = channelsResult.getOrElse { e ->
                _state.update { it.copy(loading = false, error = e) }
                return@launch
            }
            // Sort + dedupe once (10k+ channels on real portals), off the main thread; filters keep this order.
            val channels = withContext(Dispatchers.Default) { LiveFilter.prepare(unsorted) }
            val groups = LiveFilter.withSyntheticGroups(groupsResult.getOrNull().orEmpty())
            val prefs = users.preferences().getOrNull()
            val providerKey = users.providerKey().getOrNull() ?: DefaultUserRepository.FALLBACK_PROVIDER_KEY
            val current = _state.value
            // Keep a group the user already picked this session; otherwise restore the saved one.
            val selected = if (current.channels.isNotEmpty() && groups.any { it.id == current.selectedGroupId }) {
                current.selectedGroupId
            } else {
                LiveFilter.restoreGroup(prefs?.lastSelectedTvGroup?.get(providerKey), groups)
            }
            // First open: start the list at the last played channel (synced pref, as the webui does).
            if (lastFocusedChannelId == null) lastFocusedChannelId = prefs?.lastSelectedTvChannel?.get(providerKey)
            _state.update {
                it.copy(
                    loading = false,
                    error = null,
                    groups = groups,
                    channels = channels,
                    selectedGroupId = selected,
                    favorites = prefs?.favorites.orEmpty().toSet(),
                    recents = prefs?.recentChannels.orEmpty(),
                ).withVisible()
            }
            refreshEpg(forceRefresh)
        }
    }

    /** Re-read the EPG (30-minute cadence from the screen). A cold server keeps the old data. */
    fun refreshEpg(forceRefresh: Boolean) {
        viewModelScope.launch {
            live.epg(forceRefresh).onSuccess { epg ->
                if (epg != null) {
                    val index = withContext(Dispatchers.Default) { LiveEpg.index(epg) }
                    _state.update { it.copy(epg = epg, epgIndex = index) }
                }
            }
        }
    }

    fun selectGroup(groupId: String) {
        if (groupId == _state.value.selectedGroupId) return
        _state.update { it.copy(selectedGroupId = groupId).withVisible() }
    }

    /**
     * Hands the channel and the list it was picked from to the player, and records the
     * recent channel / last group (best effort, in the background). The local recents
     * update after a short delay so the Recent list does not reshuffle under the user.
     */
    fun play(channel: Channel) {
        val s = _state.value
        val playlist = s.visible.ifEmpty { listOf(channel) }
        lastFocusedChannelId = channel.id
        PlaybackHolder.start(PlaybackSession.Live(channel, playlist))
        val groupId = s.selectedGroupId.takeUnless { it == ChannelGroup.RECENT_ID || it == ChannelGroup.FAVORITES_ID }
            ?: channel.groupId
        viewModelScope.launch {
            live.recordRecent(channel.id, groupId)
            _state.update { it.copy(recents = LiveFilter.pushRecent(it.recents, channel.id)).withVisible() }
        }
    }

    /** Optimistic favourite toggle; reverted with a notice when the PUT fails. */
    fun toggleFavorite(channel: Channel) {
        val before = _state.value.favorites
        val optimistic = if (channel.id in before) before - channel.id else before + channel.id
        _state.update { it.copy(favorites = optimistic, notice = null).withVisible() }
        viewModelScope.launch {
            live.toggleFavorite(channel.id).fold(
                onSuccess = { list -> _state.update { it.copy(favorites = list.toSet()).withVisible() } },
                onFailure = { e ->
                    _state.update { it.copy(favorites = before, notice = "Couldn't update favourites: ${e.panelMessage()}").withVisible() }
                },
            )
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    private fun State.withVisible(): State =
        copy(visible = LiveFilter.filter(channels, selectedGroupId, favorites, recents))
}
