package com.portalcast.tv.data.repo

import kotlinx.serialization.builtins.ListSerializer
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
    disk: DiskCache? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : LiveRepository {

    // Groups and channels also kept in storage (DiskCache): Live TV opens at once after a
    // restart, then checks with the server. The guide is not (large, and it moves every hour).
    // Each in its own slot: both were stored as (NS, "") and overwrote each other, and channels
    // read back as groups (a group shows its "name") filled the Live TV column with channel names
    // ("21 Jump Street FHD") after a restart (owner, 2026-10-24).
    private val groupsCache = SessionCache(session, clock = clock, stored = disk?.let { Stored(it, GROUPS_NS, "groups", ListSerializer(ChannelGroup.serializer())) })
    private val channelsCache = SessionCache(session, clock = clock, stored = disk?.let { Stored(it, CHANNELS_NS, "channels", ListSerializer(Channel.serializer())) })
    private val epgCache = SessionCache<EpgData>(session, ttlMs = EPG_TTL_MS, clock = clock)
    @Volatile private var windowUnsupported = false

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
            // Parse off the main thread while streaming (EpgStream keeps only now..+6h either way).
            // Prefer the server's precomputed window; servers without it answer 404 once per
            // session and we fall back to the whole guide (~50 MB raw on big portals).
            withContext(Dispatchers.IO) {
                val body = if (windowUnsupported) api.epg() else try {
                    val window = api.epgWindow(WINDOW_HOURS)
                    // Servers from before /epg/window answer unknown paths with the webui's
                    // index.html (200, text/html), not 404: parsing that gave "no guide" forever.
                    if (window.contentType()?.subtype?.contains("json", ignoreCase = true) == true) window
                    else {
                        window.close()
                        windowUnsupported = true
                        api.epg()
                    }
                } catch (e: retrofit2.HttpException) {
                    if (e.code() != 404) throw e
                    windowUnsupported = true
                    api.epg()
                }
                body.use { EpgStream.parse(it.source(), nowSec = clock() / 1000) }
            }
        }.onSuccess { if (it != null) epgCache.put(it) }
    }

    // Not cached: the "Live" badges and guide matches move with the clock (the server caches).
    override suspend fun sports(days: Int, fresh: Boolean): Result<com.portalcast.tv.data.model.SportsResponse> =
        apiCall(ApiPaths.SPORTS) { api.sports(days, if (fresh) System.currentTimeMillis() else null) }

    override suspend fun sportsLeagues(): Result<com.portalcast.tv.data.model.SportsLeagues> =
        apiCall(ApiPaths.SPORTS) { api.sportsLeagues() }

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
        /** [DiskCache.updates] kind for Live TV answers. */
        const val NS = "live"
        /** Where groups and channels are stored (the shared [NS] slot is never read again). */
        const val GROUPS_NS = "$NS.groups"
        const val CHANNELS_NS = "$NS.channels"
        const val WINDOW_HOURS = 6
        const val EPG_TTL_MS = 30 * 60 * 1000L
        const val MAX_RECENT = 20
        const val LAST_PLAYED_TV_CHANNEL_KEY = "lastPlayedTvChannelId"
    }
}
