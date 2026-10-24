package com.portalcast.tv.data.model

import com.portalcast.tv.data.net.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FacetValue(val value: String = "", val count: LenientInt = 0, val logo: String? = null)

/** `GET /api/v2/discover/facets?type=` */
@Serializable
data class DiscoverFacets(
    val genres: List<FacetValue> = emptyList(),
    val countries: List<FacetValue> = emptyList(),
    val themes: List<FacetValue> = emptyList(),
    val languages: List<FacetValue> = emptyList(),
    /** TV channels shows air on ("Zee5", "Star Plus"), with logos. */
    val networks: List<FacetValue> = emptyList(),
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

/**
 * `GET /api/v2/home?type=` — the viewer's personal rows, prepared on the server ("Because you
 * watched X", "Top picks for you", "More with <name>", "Popular on this server"), and
 * `genreOrder`: their genres, most watched first, for the genre rows.
 */
@Serializable
data class HomeResponse(
    val rows: List<HomeRowDto> = emptyList(),
    val genreOrder: List<String> = emptyList(),
)

@Serializable
data class HomeRowDto(
    val key: String,
    val kind: String = "",
    val title: String = "",
    val data: List<MediaItem> = emptyList(),
)

/** A list of cards: `GET /api/v2/discover/similar?id=` (More Like This), `GET /api/user/list` (My List). */
@Serializable
data class CardsResponse(val data: List<MediaItem> = emptyList())

/** `GET /api/user/list/ids`: the content ids in My List. */
@Serializable
data class ListIds(val ids: List<String> = emptyList())

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
    val network: String? = null,
    /** Genres a title must not have ("not Horror"). Older servers ignore them. */
    val notGenres: List<String> = emptyList(),
) {
    val genreParam: String? get() = genres.takeIf { it.isNotEmpty() }?.joinToString(",")
    val notGenreParam: String? get() = notGenres.takeIf { it.isNotEmpty() }?.joinToString(",")

    /** A genre pressed in the list, as on the web: required, then left out, then off. */
    fun cycleGenre(value: String): DiscoverQuery = when (value) {
        in genres -> copy(genres = genres - value, notGenres = notGenres + value)
        in notGenres -> copy(notGenres = notGenres - value)
        else -> copy(genres = genres + value)
    }
}
