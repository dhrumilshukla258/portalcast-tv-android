package com.portalcast.tv.data.model

import com.portalcast.tv.data.net.LenientInt
import com.portalcast.tv.data.net.LenientStr
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A live group (`GET /api/v2/groups`, bare array) or a VOD/series category
 * (`data` of `/movie-groups`, `/series-groups`). Virtual categories are `vcat_*`
 * ids with only `title` set.
 */
@Serializable
data class ChannelGroup(
    val id: LenientStr = "",
    val title: String? = null,
    val name: String? = null,
    val number: LenientInt? = null,
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: name ?: ""

    /** The portal's own "all" pseudo-groups, which the webui drops before adding its synthetic ones. */
    val isPortalAllGroup: Boolean get() = id == "*" || id == "0" || displayTitle.equals("all", ignoreCase = true)

    companion object {
        const val ALL_ID = "*"
        const val FAVORITES_ID = "fav"
        const val RECENT_ID = "recent"
        fun synthetic(id: String, title: String) = ChannelGroup(id = id, title = title)
    }
}

/** One item of `GET /api/v2/channels` (bare array, whole list). */
@Serializable
data class Channel(
    val id: LenientStr = "",
    val name: String? = null,
    val number: LenientInt? = null,
    @SerialName("tv_genre_id") val groupId: LenientStr? = null,
    /** Raw logo value; prefer [screenshotUri]. */
    val logo: String? = null,
    @SerialName("screenshot_uri") val screenshotUri: String? = null,
    /**
     * Absolute, pre-tokenized `<origin>/live.m3u8?t=…&id=…&proxy=1`. Play this
     * directly as HLS. Reusable for ~4h; on 401/403 refetch the channel list.
     */
    val cmd: String? = null,
    @SerialName("enable_tv_archive") val archiveEnabled: LenientInt? = null,
    @SerialName("tv_archive_duration") val archiveDuration: LenientStr? = null,
    val isPortal: Boolean? = null,
) {
    val displayName: String get() = name ?: ""
    val artwork: String? get() = screenshotUri ?: logo
}
