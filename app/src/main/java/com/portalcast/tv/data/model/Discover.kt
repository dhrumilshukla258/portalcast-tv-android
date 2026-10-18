package com.portalcast.tv.data.model

import com.portalcast.tv.data.net.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FacetValue(val value: String = "", val count: LenientInt = 0)

/** `GET /api/v2/discover/facets?type=` */
@Serializable
data class DiscoverFacets(
    val genres: List<FacetValue> = emptyList(),
    val countries: List<FacetValue> = emptyList(),
    val themes: List<FacetValue> = emptyList(),
    val languages: List<FacetValue> = emptyList(),
)

/** `GET /api/v2/discover/browse` — page size fixed at 40. */
@Serializable
data class DiscoverBrowseResponse(
    val data: List<MediaItem> = emptyList(),
    val page: LenientInt? = null,
    @SerialName("total_items") val totalItems: LenientInt? = null,
) {
    fun hasMore(loadedSoFar: Int): Boolean = (totalItems ?: 0) > loadedSoFar
}

/** `GET /api/v2/discover/recommendations?type=` — `basedOnTitle` is sometimes absent. */
@Serializable
data class RecommendationsResponse(
    val data: List<MediaItem> = emptyList(),
    val basedOnTitle: String? = null,
)

/** `GET /api/v2/discover/variants?id=movie_123` */
@Serializable
data class VariantsResponse(val variants: List<MediaItem> = emptyList())

/** Query for `discover/browse`. `genre` is comma-joined (AND) by the server. */
data class DiscoverQuery(
    val type: String? = null,            // "movie" | "series"
    val genres: List<String> = emptyList(),
    val country: String? = null,
    val language: String? = null,
    val theme: String? = null,
) {
    val genreParam: String? get() = genres.takeIf { it.isNotEmpty() }?.joinToString(",")
}
