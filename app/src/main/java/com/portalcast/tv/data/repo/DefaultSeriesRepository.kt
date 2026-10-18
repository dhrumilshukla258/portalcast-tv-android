package com.portalcast.tv.data.repo

import com.portalcast.tv.data.model.ChannelGroup
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.Paged
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.net.ApiService
import com.portalcast.tv.data.net.apiCall

/** Series categories, paged lists, seasons, episodes and episode files (contract §3.3). */
class DefaultSeriesRepository(private val api: ApiService) : SeriesRepository {

    override suspend fun groups(): Result<List<ChannelGroup>> =
        apiCall(ApiPaths.SERIES_GROUPS) { withAllGroup(api.seriesGroups().data) }

    override suspend fun series(category: String, page: Int, search: String?, sort: String?): Result<Paged<MediaItem>> =
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
        apiCall(ApiPaths.SERIES) {
            val (page, _) = lookupWithAllFallback(categoryOrAll(category)) { c -> api.series(category = c, movieId = seriesId) }
            page.data
        }

    override suspend fun episodes(seriesId: String, seasonId: String, page: Int, category: String?): Result<Paged<MediaItem>> =
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
