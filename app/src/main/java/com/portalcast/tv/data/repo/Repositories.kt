package com.portalcast.tv.data.repo

import com.portalcast.tv.data.model.ChannelGroup
import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.DeviceCodeResponse
import com.portalcast.tv.data.model.DevicePollResponse
import com.portalcast.tv.data.model.DiscoverBrowseResponse
import com.portalcast.tv.data.model.DiscoverFacets
import com.portalcast.tv.data.model.DiscoverQuery
import com.portalcast.tv.data.model.EpgData
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.Paged
import com.portalcast.tv.data.model.Preferences
import com.portalcast.tv.data.model.ProgressRecord
import com.portalcast.tv.data.model.RecommendationsResponse
import com.portalcast.tv.data.model.HomeResponse
import com.portalcast.tv.data.model.UserDto
import com.portalcast.tv.data.store.Session
import kotlinx.coroutines.flow.StateFlow

/*
 * Repository contracts. Phase 0 fixes these so UI and playback work can proceed
 * against them; Phase 1a (data-layer agent) implements them in Default*.kt.
 * Every suspend call returns Result<T> whose failure is an ApiError.
 */

interface AuthRepository {
    val isLoaded: StateFlow<Boolean>
    val serverUrl: StateFlow<String?>
    val session: StateFlow<Session?>

    /** Normalises and stores the URL after checking the server answers (any HTTP response counts). */
    suspend fun setServerUrl(input: String): Result<Unit>
    suspend fun requestDeviceCode(): Result<DeviceCodeResponse>
    /** One call = one poll. The caller owns the 4 s cadence and must stop on authorized/expired. */
    suspend fun pollDeviceCode(deviceCode: String): Result<DevicePollResponse>
    /** Persists the session from an `authorized` poll or a password login. */
    suspend fun acceptTokens(accessToken: String, refreshToken: String, user: UserDto)
    suspend fun loginWithPassword(email: String, password: String): Result<Unit>
    /** `GET /api/user/profile`; updates the stored user. Unauthorized → session cleared. */
    suspend fun refreshProfile(): Result<UserDto>
    suspend fun logout()
}

interface UserRepository {
    /** Cached for the session; "stalker_unknown" when the route is missing. */
    suspend fun providerKey(): Result<String>
    suspend fun preferences(forceRefresh: Boolean = false): Result<Preferences>
    /** Sends only the non-null fields of [patch]; maps must be whole. Returns merged prefs. */
    suspend fun updatePreferences(patch: Preferences): Result<Preferences>
    suspend fun progress(forceRefresh: Boolean = false): Result<List<ProgressRecord>>
    suspend fun saveProgress(record: ProgressRecord): Result<Unit>
    suspend fun deleteProgress(mediaId: String): Result<Unit>
    /** Not-completed, non-tv rows sorted by meta.timestamp desc, deduped (contract §6.2). */
    suspend fun continueWatching(): Result<List<ProgressRecord>>
}

interface LiveRepository {
    /** Server groups with portal "all" groups removed; the UI prepends Recent/Favourites/All itself. */
    suspend fun groups(forceRefresh: Boolean = false): Result<List<ChannelGroup>>
    /** Whole channel list, cached per session. Force-refresh on a stream 401/403. */
    suspend fun channels(forceRefresh: Boolean = false): Result<List<Channel>>
    /** null when the server has no EPG yet. Cached; refresh every 30 min while Live is visible. */
    suspend fun epg(forceRefresh: Boolean = false): Result<EpgData?>
    suspend fun favorites(): Result<List<String>>
    suspend fun toggleFavorite(channelId: String): Result<List<String>>
    suspend fun recordRecent(channelId: String, groupId: String?)
}

interface VodRepository {
    suspend fun groups(): Result<List<ChannelGroup>>
    suspend fun movies(category: String, page: Int, search: String? = null, sort: String? = null): Result<Paged<MediaItem>>
    /** `movies?movieId=&category=` → data[0]; the playable file for a movie. */
    suspend fun resolveFile(movieId: String, category: String?): Result<MediaItem>
}

interface SeriesRepository {
    suspend fun groups(): Result<List<ChannelGroup>>
    suspend fun series(category: String, page: Int, search: String? = null, sort: String? = null): Result<Paged<MediaItem>>
    /** Seasons of a series (`data[0]` is a season). Retries with "*" when [category] yields nothing. */
    suspend fun seasons(seriesId: String, category: String?): Result<List<MediaItem>>
    suspend fun episodes(seriesId: String, seasonId: String, page: Int, category: String?): Result<Paged<MediaItem>>
    /** `movies?movieId=<seriesId>&seasonId=&episodeId=&category=*` → data[0]. */
    suspend fun resolveEpisodeFile(seriesId: String, seasonId: String, episodeId: String): Result<MediaItem>
}

interface DiscoverRepository {
    suspend fun facets(type: String?): Result<DiscoverFacets>
    suspend fun browse(query: DiscoverQuery, page: Int): Result<DiscoverBrowseResponse>
    suspend fun recommendations(type: String?): Result<RecommendationsResponse>
    /** The viewer's prepared rows (server Phase 2). Default: unsupported, so callers fall back. */
    suspend fun home(type: String?): Result<HomeResponse> = Result.failure(UnsupportedOperationException("home"))
    /** More Like This (server Phase 6). Default: none. */
    suspend fun similar(contentId: String): Result<List<MediaItem>> = Result.success(emptyList())
    /** My List (server Phase 6). Defaults: unsupported, so the button stays hidden. */
    suspend fun myList(): Result<List<MediaItem>> = Result.success(emptyList())
    suspend fun myListIds(): Result<Set<String>> = Result.failure(UnsupportedOperationException("my list"))
    suspend fun setInList(contentId: String, inList: Boolean): Result<Unit> = Result.failure(UnsupportedOperationException("my list"))
    suspend fun variants(prefixedId: String): Result<List<MediaItem>>
    /**
     * Resolve a Discover item (`movie_<id>` / `series_<id>`) into the regular list item
     * the detail screens expect, keeping Discover's title and stamping `category_id`.
     */
    suspend fun openItem(item: MediaItem): Result<MediaItem>

    /** A person's page (`GET /api/v2/people/{id}`). */
    suspend fun person(id: Int): Result<com.portalcast.tv.data.model.PersonPage> =
        Result.failure(UnsupportedOperationException("person pages"))

    /** People by name (`GET /api/v2/search/people`). */
    suspend fun searchPeople(query: String): Result<com.portalcast.tv.data.model.PeopleSearchResult> =
        Result.failure(UnsupportedOperationException("people search"))
}
