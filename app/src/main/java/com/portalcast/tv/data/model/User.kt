package com.portalcast.tv.data.model

import com.portalcast.tv.data.net.AppJson
import com.portalcast.tv.data.net.LenientBool
import com.portalcast.tv.data.net.LenientDouble
import com.portalcast.tv.data.net.LenientInt
import com.portalcast.tv.data.net.LenientStr
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.encodeToJsonElement

/**
 * `User.preferences` as stored server-side. Only the keys below survive a PUT
 * (server allowlist, contract §6.1). Maps are shallow-merged per top-level key,
 * so always send a whole map, never a single entry.
 */
@Serializable
data class Preferences(
    val preferredContentType: String? = null,            // "movie" | "series" | "tv"
    val favorites: List<String>? = null,                 // channel ids
    val recentChannels: List<String>? = null,            // max 20, newest first
    val videoFitMode: String? = null,
    val lastSelectedCategory: Map<String, String>? = null,      // "<providerKey>_movie" -> id, plus "lastPlayedTvChannelId"
    val lastSelectedCategoryTitle: Map<String, String>? = null,
    val lastSelectedTvGroup: Map<String, String>? = null,       // providerKey -> group id
    val lastSelectedTvChannel: Map<String, String>? = null,     // providerKey -> channel id
    val recentCategories: Map<String, List<String>>? = null,
    val pinnedCategories: Map<String, List<String>>? = null,
    val categoryOrder: Map<String, List<String>>? = null,
    val recentSearches: List<String>? = null,
) {
    /** Encodes only the non-null fields, which is exactly what a partial PUT needs. */
    fun toPatch(): JsonObject = AppJson.encodeToJsonElement(this).jsonObject
}

@Serializable
data class PreferencesResponse(
    val success: Boolean? = null,
    val preferences: Preferences? = null,
)

/**
 * One row of `GET /api/user/progress`; also the body of `PUT /api/user/progress`.
 * `progress`/`completed`/`meta` carry [EncodeDefault] so a PUT always sends all four
 * contract fields even at their defaults (AppJson has `encodeDefaults = false`).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ProgressRecord(
    val mediaId: LenientStr,
    /** Seconds. */
    @EncodeDefault val progress: LenientDouble = 0.0,
    @EncodeDefault val completed: LenientBool = false,
    @EncodeDefault val meta: JsonObject = JsonObject(emptyMap()),
    val updatedAt: String? = null,
) {
    val parsedMeta: ProgressMeta? get() = runCatching { AppJson.decodeFromJsonElement(ProgressMeta.serializer(), meta) }.getOrNull()
}

/**
 * The `meta` object the webui writes (contract §6.2). Mirror it field for field or
 * Continue Watching and Discover recommendations will not line up across devices.
 */
@Serializable
data class ProgressMeta(
    val id: String? = null,
    val playbackFileId: String? = null,
    val mediaId: String? = null,
    val itemId: String? = null,
    val seasonId: String? = null,
    val categoryId: String? = null,
    val type: String? = null,            // "movie" | "series"
    val title: String? = null,
    val currentTime: LenientDouble? = null,
    val duration: LenientDouble? = null,
    val progressPercent: LenientInt? = null,
    val timestamp: Long? = null,         // ms epoch
    val name: String? = null,
    val episodeTitle: String? = null,
    val screenshot_uri: String? = null,
    val is_series: LenientInt? = null,   // 0 | 1
    val cmd: String? = null,
    val series_number: LenientInt? = null,
    val catalogId: String? = null,       // "movie_<id>" | "series_<id>"
) {
    fun toJson(): JsonObject = AppJson.encodeToJsonElement(this).jsonObject
}
