package com.portalcast.tv.data.repo

import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.ChannelGroup
import com.portalcast.tv.data.model.EpgData
import com.portalcast.tv.data.net.EpgStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.portalcast.tv.data.model.Preferences
import com.portalcast.tv.data.net.ApiService
import com.portalcast.tv.data.net.apiCall
import com.portalcast.tv.data.store.SessionSource

/**
 * Live groups, channels, EPG, favourites and recents (contract §3.1, §3.5, §6.1).
 * Favourites and recents are plain preference keys, so they go through
 * [UserRepository.updatePreferences] with WHOLE lists and maps.
 */
class DefaultLiveRepository(
    private val api: ApiService,
    private val users: UserRepository,
    private val session: SessionSource? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : LiveRepository {

    private val groupsCache = SessionCache<List<ChannelGroup>>(session, clock = clock)
    private val channelsCache = SessionCache<List<Channel>>(session, clock = clock)
    private val epgCache = SessionCache<EpgData>(session, ttlMs = EPG_TTL_MS, clock = clock)

    /** Drop everything cached (e.g. on a `config_changed` socket event). */
    fun clearCaches() {
        groupsCache.clear()
        channelsCache.clear()
        epgCache.clear()
    }

    override suspend fun groups(forceRefresh: Boolean): Result<List<ChannelGroup>> = groupsCache.get(forceRefresh) {
        apiCall(ApiPaths.GROUPS) { api.liveGroups().filterNot { it.isPortalAllGroup } }
    }

    override suspend fun channels(forceRefresh: Boolean): Result<List<Channel>> = channelsCache.get(forceRefresh) {
        apiCall(ApiPaths.CHANNELS) { api.channels() }
    }

    /** A cold server (`[]`) yields `null` and is not cached, so the next call asks again. */
    override suspend fun epg(forceRefresh: Boolean): Result<EpgData?> {
        if (!forceRefresh) epgCache.peek()?.let { return Result.success(it) }
        return apiCall(ApiPaths.EPG) {
            // Parse off the main thread while streaming: the body can be ~50 MB (see EpgStream).
            withContext(Dispatchers.IO) { api.epg().use { body -> EpgStream.parse(body.source(), nowSec = clock() / 1000) } }
        }.onSuccess { if (it != null) epgCache.put(it) }
    }

    override suspend fun favorites(): Result<List<String>> = users.preferences().map { it.favorites.orEmpty() }

    override suspend fun toggleFavorite(channelId: String): Result<List<String>> {
        val current = favorites().getOrElse { return Result.failure(it) }
        val next = if (channelId in current) current.filterNot { it == channelId } else current + channelId
        return users.updatePreferences(Preferences(favorites = next)).map { it.favorites ?: next }
    }

    /**
     * Newest-first recents capped at [MAX_RECENT], plus the provider-keyed last
     * channel/group and `lastSelectedCategory.lastPlayedTvChannelId`, in one PUT.
     * Existing map entries are preserved. Best effort: failures are swallowed.
     */
    override suspend fun recordRecent(channelId: String, groupId: String?) {
        if (channelId.isBlank()) return
        val prefs = users.preferences().getOrNull() ?: Preferences()
        val providerKey = users.providerKey().getOrNull() ?: DefaultUserRepository.FALLBACK_PROVIDER_KEY
        val recents = (listOf(channelId) + prefs.recentChannels.orEmpty().filterNot { it == channelId }).take(MAX_RECENT)
        val patch = Preferences(
            recentChannels = recents,
            lastSelectedTvChannel = prefs.lastSelectedTvChannel.orEmpty() + (providerKey to channelId),
            lastSelectedTvGroup = groupId?.takeIf { it.isNotBlank() }?.let { prefs.lastSelectedTvGroup.orEmpty() + (providerKey to it) },
            lastSelectedCategory = prefs.lastSelectedCategory.orEmpty() + (LAST_PLAYED_TV_CHANNEL_KEY to channelId),
        )
        users.updatePreferences(patch)
    }

    companion object {
        const val EPG_TTL_MS = 30 * 60 * 1000L
        const val MAX_RECENT = 20
        const val LAST_PLAYED_TV_CHANNEL_KEY = "lastPlayedTvChannelId"
    }
}
