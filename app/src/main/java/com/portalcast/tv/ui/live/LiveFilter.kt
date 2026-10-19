package com.portalcast.tv.ui.live

import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.ChannelGroup

/** Client-side group logic for the Live tab (the channel list is fetched whole). Pure, JVM-tested. */
object LiveFilter {

    private val syntheticIds = setOf(ChannelGroup.RECENT_ID, ChannelGroup.FAVORITES_ID, ChannelGroup.ALL_ID)

    /** Recent, Favourites, All, then the server's groups (any server copies of those ids dropped). */
    fun withSyntheticGroups(server: List<ChannelGroup>): List<ChannelGroup> = listOf(
        ChannelGroup.synthetic(ChannelGroup.RECENT_ID, "Recent"),
        ChannelGroup.synthetic(ChannelGroup.FAVORITES_ID, "Favourites"),
        ChannelGroup.synthetic(ChannelGroup.ALL_ID, "All"),
    ) + server.filterNot { it.id in syntheticIds || it.isPortalAllGroup }

    /**
     * Channels shown for [groupId]:
     * - Recent: in [recents] order (newest first), unknown ids skipped;
     * - Favourites: channel-list order, only ids in [favorites];
     * - All: everything;
     * - otherwise `tv_genre_id == groupId`.
     */
    fun filter(all: List<Channel>, groupId: String, favorites: Collection<String>, recents: List<String>): List<Channel> =
        when (groupId) {
            ChannelGroup.RECENT_ID -> {
                val byId = HashMap<String, Channel>(all.size)
                for (c in all) byId.putIfAbsent(c.id, c)
                recents.distinct().mapNotNull { byId[it] }
            }
            ChannelGroup.FAVORITES_ID -> {
                val set = favorites.toHashSet()
                all.filter { it.id in set }.sortedWith(byNumber)
            }
            ChannelGroup.ALL_ID -> all.sortedWith(byNumber)
            else -> all.filter { it.groupId == groupId }.sortedWith(byNumber)
        }

    /** TV order: channel number first (the server sorts by name), unnumbered last by name. Recent keeps recency. */
    val byNumber: Comparator<Channel> = compareBy<Channel>({ it.number == null || it.number == 0 }, { it.number ?: 0 }, { it.displayName.lowercase() })

    /** The saved group when it still exists, else All. */
    fun restoreGroup(saved: String?, groups: List<ChannelGroup>): String =
        saved?.takeIf { s -> s.isNotBlank() && groups.any { it.id == s } } ?: ChannelGroup.ALL_ID

    /** Recents after playing [channelId]: moved to the front, capped at [max]. */
    fun pushRecent(recents: List<String>, channelId: String, max: Int = 20): List<String> =
        (listOf(channelId) + recents.filterNot { it == channelId }).take(max)
}
