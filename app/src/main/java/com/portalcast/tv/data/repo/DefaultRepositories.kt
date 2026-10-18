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
import com.portalcast.tv.data.model.UserDto
import com.portalcast.tv.data.net.ApiService
import com.portalcast.tv.data.store.Session
import com.portalcast.tv.data.store.SessionStore
import kotlinx.coroutines.flow.StateFlow

/*
 * Phase 0 skeletons. Everything that touches the network throws NotImplementedError
 * until Phase 1a replaces the bodies. Keep constructor signatures stable: AppContainer
 * wires these.
 */

private fun notYet(): Nothing = throw NotImplementedError("Phase 1a: data layer not implemented yet")

class DefaultAuthRepository(
    private val api: ApiService,
    private val sessionStore: SessionStore,
) : AuthRepository {
    override val isLoaded: StateFlow<Boolean> get() = sessionStore.isLoaded
    override val serverUrl: StateFlow<String?> get() = sessionStore.serverUrl
    override val session: StateFlow<Session?> get() = sessionStore.session

    override suspend fun setServerUrl(input: String): Result<Unit> = notYet()
    override suspend fun requestDeviceCode(): Result<DeviceCodeResponse> = notYet()
    override suspend fun pollDeviceCode(deviceCode: String): Result<DevicePollResponse> = notYet()
    override suspend fun acceptTokens(accessToken: String, refreshToken: String, user: UserDto) = sessionStore.saveSession(accessToken, refreshToken, user)
    override suspend fun loginWithPassword(email: String, password: String): Result<Unit> = notYet()
    override suspend fun refreshProfile(): Result<UserDto> = notYet()
    override suspend fun logout() = sessionStore.clearSession()
}

class DefaultUserRepository(private val api: ApiService) : UserRepository {
    override suspend fun providerKey(): Result<String> = notYet()
    override suspend fun preferences(forceRefresh: Boolean): Result<Preferences> = notYet()
    override suspend fun updatePreferences(patch: Preferences): Result<Preferences> = notYet()
    override suspend fun progress(forceRefresh: Boolean): Result<List<ProgressRecord>> = notYet()
    override suspend fun saveProgress(record: ProgressRecord): Result<Unit> = notYet()
    override suspend fun deleteProgress(mediaId: String): Result<Unit> = notYet()
    override suspend fun continueWatching(): Result<List<ProgressRecord>> = notYet()
}

class DefaultLiveRepository(private val api: ApiService, private val users: UserRepository) : LiveRepository {
    override suspend fun groups(forceRefresh: Boolean): Result<List<ChannelGroup>> = notYet()
    override suspend fun channels(forceRefresh: Boolean): Result<List<Channel>> = notYet()
    override suspend fun epg(forceRefresh: Boolean): Result<EpgData?> = notYet()
    override suspend fun favorites(): Result<List<String>> = notYet()
    override suspend fun toggleFavorite(channelId: String): Result<List<String>> = notYet()
    override suspend fun recordRecent(channelId: String, groupId: String?) = notYet()
}

class DefaultVodRepository(private val api: ApiService) : VodRepository {
    override suspend fun groups(): Result<List<ChannelGroup>> = notYet()
    override suspend fun movies(category: String, page: Int, search: String?, sort: String?): Result<Paged<MediaItem>> = notYet()
    override suspend fun resolveFile(movieId: String, category: String?): Result<MediaItem> = notYet()
}

class DefaultSeriesRepository(private val api: ApiService) : SeriesRepository {
    override suspend fun groups(): Result<List<ChannelGroup>> = notYet()
    override suspend fun series(category: String, page: Int, search: String?, sort: String?): Result<Paged<MediaItem>> = notYet()
    override suspend fun seasons(seriesId: String, category: String?): Result<List<MediaItem>> = notYet()
    override suspend fun episodes(seriesId: String, seasonId: String, page: Int, category: String?): Result<Paged<MediaItem>> = notYet()
    override suspend fun resolveEpisodeFile(seriesId: String, seasonId: String, episodeId: String): Result<MediaItem> = notYet()
}

class DefaultDiscoverRepository(
    private val api: ApiService,
    private val vod: VodRepository,
    private val series: SeriesRepository,
) : DiscoverRepository {
    override suspend fun facets(type: String?): Result<DiscoverFacets> = notYet()
    override suspend fun browse(query: DiscoverQuery, page: Int): Result<DiscoverBrowseResponse> = notYet()
    override suspend fun recommendations(type: String?): Result<RecommendationsResponse> = notYet()
    override suspend fun variants(prefixedId: String): Result<List<MediaItem>> = notYet()
    override suspend fun openItem(item: MediaItem): Result<MediaItem> = notYet()
}
