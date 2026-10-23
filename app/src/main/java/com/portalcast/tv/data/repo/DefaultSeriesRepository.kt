package com.portalcast.tv.data.repo

import kotlinx.serialization.builtins.ListSerializer
import com.portalcast.tv.data.store.SessionSource
import com.portalcast.tv.data.model.ChannelGroup
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.Paged
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.net.ApiService
import com.portalcast.tv.data.net.apiCall

/** Series categories, paged lists, seasons, episodes and episode files (contract §3.3). */
class DefaultSeriesRepository(
    private val api: ApiService,
    session: SessionSource? = null,
    disk: DiskCache? = null,
) : SeriesRepository {

    // Lists, seasons and episode pages are cached for 15 min per session (see
    // KeyedSessionCache); episode-file resolution never is (fresh stream links).
    // Also kept in storage (DiskCache): after a restart shown at once, then checked with the server.
    private val groupsCache = SessionCache(session, ttlMs = KeyedSessionCache.DEFAULT_TTL_MS, stored = disk?.let { Stored(it, NS, ListSerializer(ChannelGroup.serializer())) })
    private val pages = KeyedSessionCache<String, Paged<MediaItem>>(session, stored = disk?.let { Stored(it, NS, Paged.serializer(MediaItem.serializer())) })
    private val seasonsCache = KeyedSessionCache<String, List<MediaItem>>(session, maxEntries = 40, stored = disk?.let { Stored(it, NS, ListSerializer(MediaItem.serializer())) })

    companion object {
        /** [DiskCache.updates] kind for series answers. */
        const val NS = "series"
    }

    fun clearCaches() { groupsCache.clear(); pages.clear(); seasonsCache.clear() }


    override suspend fun groups(): Result<List<ChannelGroup>> = groupsCache.get {
        apiCall(ApiPaths.SERIES_GROUPS) { withAllGroup(api.seriesGroups().data) }
    }

    override suspend fun series(category: String, page: Int, search: String?, sort: String?): Result<Paged<MediaItem>> =
        pages.get("list|${categoryOrAll(category)}|${page.coerceAtLeast(1)}|${search.orEmpty().trim()}|${sort.orEmpty()}") {
            seriesUncached(category, page, search, sort)
        }

    private suspend fun seriesUncached(category: String, page: Int, search: String?, sort: String?): Result<Paged<MediaItem>> =
        apiCall(ApiPaths.SERIES) {
            api.series(
                category = categoryOrAll(category),
                page = page.coerceAtLeast(1),
                search = search?.takeIf { it.isNotBlank() },
                sort = sort?.takeIf { it.isNotBlank() },
            )
        }

    /** `data` is the season list (`data[0]` is a season, never the series). */
    override suspend fun seasons(seriesId: String, category: String?): Result<List<MediaItem>> =
        seasonsCache.get(seriesId) { seasonsUncached(seriesId, category) }

    private suspend fun seasonsUncached(seriesId: String, category: String?): Result<List<MediaItem>> =
        apiCall(ApiPaths.SERIES) {
            val (page, _) = lookupWithAllFallback(categoryOrAll(category)) { c -> api.series(category = c, movieId = seriesId) }
            page.data
        }

    override suspend fun episodes(seriesId: String, seasonId: String, page: Int, category: String?): Result<Paged<MediaItem>> =
        pages.get("ep|$seriesId|$seasonId|${page.coerceAtLeast(1)}") { episodesUncached(seriesId, seasonId, page, category) }

    private suspend fun episodesUncached(seriesId: String, seasonId: String, page: Int, category: String?): Result<Paged<MediaItem>> =
        apiCall(ApiPaths.SERIES) {
            api.series(category = categoryOrAll(category), page = page.coerceAtLeast(1), movieId = seriesId, seasonId = seasonId)
        }

    /** Yes, the movies route: `movies?movieId=<seriesId>&seasonId=&episodeId=&category=*` → `data[0]`. */
    override suspend fun resolveEpisodeFile(seriesId: String, seasonId: String, episodeId: String): Result<MediaItem> =
        apiCall(ApiPaths.MOVIES) {
            api.movies(category = ALL_CATEGORY, movieId = seriesId, seasonId = seasonId, episodeId = episodeId)
                .data.firstOrNull() ?: throw ApiError.Malformed("No playable file for episode $episodeId")
        }
}
