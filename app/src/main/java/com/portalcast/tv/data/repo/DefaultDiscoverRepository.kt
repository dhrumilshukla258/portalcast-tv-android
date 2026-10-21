package com.portalcast.tv.data.repo

import com.portalcast.tv.data.store.SessionSource
import com.portalcast.tv.data.model.DiscoverBrowseResponse
import com.portalcast.tv.data.model.DiscoverFacets
import com.portalcast.tv.data.model.DiscoverQuery
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.RecommendationsResponse
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.net.ApiService
import com.portalcast.tv.data.net.AppJson
import com.portalcast.tv.data.net.apiCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Discover facets, rows, recommendations, variants and open-item resolution (contract §3.6). */
class DefaultDiscoverRepository(
    private val api: ApiService,
    @Suppress("unused") private val vod: VodRepository,
    @Suppress("unused") private val series: SeriesRepository,
    session: SessionSource? = null,
) : DiscoverRepository {

    // Discover data only changes when the server re-enriches: cache it for 15 min per
    // session so switching tabs or coming back from a title is instant. Recommendations
    // are per user but just as stable over that window.
    private val facetsCache = KeyedSessionCache<String, DiscoverFacets>(session, maxEntries = 4)
    private val browseCache = KeyedSessionCache<String, DiscoverBrowseResponse>(session)
    private val recsCache = KeyedSessionCache<String, RecommendationsResponse>(session, maxEntries = 4)
    private val variantsCache = KeyedSessionCache<String, List<MediaItem>>(session, maxEntries = 60)

    fun clearCaches() { facetsCache.clear(); browseCache.clear(); recsCache.clear(); variantsCache.clear() }

    override suspend fun facets(type: String?): Result<DiscoverFacets> =
        facetsCache.get(type.orEmpty()) { apiCall(ApiPaths.DISCOVER_FACETS) { api.discoverFacets(type) } }

    override suspend fun browse(query: DiscoverQuery, page: Int): Result<DiscoverBrowseResponse> =
        browseCache.get("${query.type}|${query.genreParam}|${query.country}|${query.language}|${query.theme}|${query.network}|${page.coerceAtLeast(1)}") {
            browseUncached(query, page)
        }

    private suspend fun browseUncached(query: DiscoverQuery, page: Int): Result<DiscoverBrowseResponse> =
        apiCall(ApiPaths.DISCOVER_BROWSE) {
            api.discoverBrowse(
                type = query.type,
                genre = query.genreParam,
                country = query.country?.takeIf { it.isNotBlank() },
                language = query.language?.takeIf { it.isNotBlank() },
                theme = query.theme?.takeIf { it.isNotBlank() },
                network = query.network?.takeIf { it.isNotBlank() },
                page = page.coerceAtLeast(1),
            )
        }

    override suspend fun recommendations(type: String?): Result<RecommendationsResponse> =
        recsCache.get(type.orEmpty()) { apiCall(ApiPaths.DISCOVER_RECOMMENDATIONS) { api.discoverRecommendations(type) } }

    override suspend fun variants(prefixedId: String): Result<List<MediaItem>> =
        variantsCache.get(prefixedId) { apiCall(ApiPaths.DISCOVER_VARIANTS) { api.discoverVariants(prefixedId).variants } }

    /**
     * Mirrors the webui's `openDiscoverItem`: strip the `movie_`/`series_` prefix, look the
     * raw id up under Discover's category, retry under `*` if that is stale, then
     *
     * - movies: the file object (`data[0]`) merged over the Discover item, keeping Discover's
     *   title/name/poster (the file's `name` is usually a quality string), `category_id` set to
     *   the category that worked and `catalogId` to the prefixed id. Its `id` is the FILE id
     *   and `cmd` is present, which tells the play path the item is already resolved.
     * - series: the lookup only proves existence (`data` is the season list), so the Discover
     *   item itself comes back with the raw id, `is_series`, `category_id` and `catalogId`.
     */
    override suspend fun person(id: Int): Result<com.portalcast.tv.data.model.PersonPage> =
        apiCall(ApiPaths.PERSON) { api.person(id) }

    override suspend fun searchPeople(query: String): Result<com.portalcast.tv.data.model.PeopleSearchResult> =
        apiCall(ApiPaths.PEOPLE_SEARCH) { api.searchPeople(query) }

    override suspend fun openItem(item: MediaItem): Result<MediaItem> {
        val isSeries = item.discoverType == "series"
        return apiCall(if (isSeries) ApiPaths.SERIES else ApiPaths.MOVIES) {
            val rawId = item.rawId
            if (rawId.isBlank()) throw ApiError.Malformed("Discover item has no id")
            val catalogId = if (item.id.startsWith("movie_") || item.id.startsWith("series_")) item.id
            else item.catalogId ?: ((if (isSeries) "series_" else "movie_") + rawId)
            val first = categoryOrAll(item.category)
            if (isSeries) {
                val (page, used) = lookupWithAllFallback(first) { c -> api.series(category = c, movieId = rawId) }
                if (page.data.isEmpty()) throw ApiError.Malformed("Series $rawId not found")
                item.copy(id = rawId, type = "series", catalogId = catalogId, isSeries = true, categoryId = used, category = used)
            } else {
                val (page, used) = lookupWithAllFallback(first) { c -> api.movies(category = c, movieId = rawId) }
                val file = page.data.firstOrNull() ?: throw ApiError.Malformed("Movie $rawId not found")
                mergeDiscoverItem(item, file).copy(type = "movie", catalogId = catalogId, categoryId = used, category = used)
            }
        }
    }

    companion object {
        /**
         * `{ ...item, ...file }` then Discover's title/poster re-applied and cast/director taken
         * from whichever side is non-empty, exactly like the webui. Done at the JSON level so
         * every field is covered without a 45-field copy.
         */
        internal fun mergeDiscoverItem(item: MediaItem, file: MediaItem): MediaItem {
            val base = AppJson.encodeToJsonElement(MediaItem.serializer(), item).jsonObject
            val top = AppJson.encodeToJsonElement(MediaItem.serializer(), file).jsonObject
            val merged = AppJson.decodeFromJsonElement(MediaItem.serializer(), JsonObject(base + top))
            val title = item.displayTitle.takeIf { it.isNotBlank() }
            return merged.copy(
                title = title ?: merged.title,
                name = title ?: merged.name,
                screenshotUri = item.screenshotUri?.takeIf { it.isNotBlank() } ?: merged.screenshotUri,
                actors = file.actors?.takeIf { it.isNotBlank() } ?: item.actors,
                director = file.director?.takeIf { it.isNotBlank() } ?: item.director,
            )
        }
    }
}
