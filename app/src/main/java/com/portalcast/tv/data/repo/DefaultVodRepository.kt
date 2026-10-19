package com.portalcast.tv.data.repo

import com.portalcast.tv.data.store.SessionSource
import com.portalcast.tv.data.model.ChannelGroup
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.Paged
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.net.ApiService
import com.portalcast.tv.data.net.apiCall

/** Movie categories, paged lists and file resolution (contract §3.2). */
class DefaultVodRepository(
    private val api: ApiService,
    session: SessionSource? = null,
) : VodRepository {

    // Lists and pages are cached for 15 min per session (see KeyedSessionCache); file
    // resolution never is (it mints fresh stream links).
    private val groupsCache = SessionCache<List<ChannelGroup>>(session, ttlMs = KeyedSessionCache.DEFAULT_TTL_MS)
    private val pages = KeyedSessionCache<String, Paged<MediaItem>>(session)

    fun clearCaches() { groupsCache.clear(); pages.clear() }


    override suspend fun groups(): Result<List<ChannelGroup>> = groupsCache.get {
        apiCall(ApiPaths.MOVIE_GROUPS) { withAllGroup(api.movieGroups().data) }
    }

    override suspend fun movies(category: String, page: Int, search: String?, sort: String?): Result<Paged<MediaItem>> =
        pages.get("${categoryOrAll(category)}|${page.coerceAtLeast(1)}|${search.orEmpty().trim()}|${sort.orEmpty()}") {
            moviesUncached(category, page, search, sort)
        }

    private suspend fun moviesUncached(category: String, page: Int, search: String?, sort: String?): Result<Paged<MediaItem>> =
        apiCall(ApiPaths.MOVIES) {
            api.movies(
                category = categoryOrAll(category),
                page = page.coerceAtLeast(1),
                search = search?.takeIf { it.isNotBlank() },
                sort = sort?.takeIf { it.isNotBlank() },
            )
        }

    /** `data[0]` of `movies?movieId=&category=`; retries with `*` when the category is stale. */
    override suspend fun resolveFile(movieId: String, category: String?): Result<MediaItem> =
        apiCall(ApiPaths.MOVIES) {
            val (page, _) = lookupWithAllFallback(categoryOrAll(category)) { c -> api.movies(category = c, movieId = movieId) }
            page.data.firstOrNull() ?: throw ApiError.Malformed("No playable file for movie $movieId")
        }
}
