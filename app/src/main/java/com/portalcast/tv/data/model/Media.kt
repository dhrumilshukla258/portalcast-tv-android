package com.portalcast.tv.data.model

import com.portalcast.tv.data.net.LenientBool
import com.portalcast.tv.data.net.LenientInt
import com.portalcast.tv.data.net.LenientStr
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One item from any of: `/api/v2/movies` (list item, detail/file), `/api/v2/series`
 * (series item, season, episode), Discover rows, carousel resolution. Mirrors the
 * webui's `MediaItem` type: every field optional, raw portal fields tolerated.
 *
 * Never play [cmd] from a list or detail response and never show [streamIcon] or
 * [cover]; use the movie-link route and [screenshotUri] (contract §3, §7).
 */
@Serializable
data class MediaItem(
    val id: LenientStr = "",
    /** Discover-sourced only: "movie" | "series". */
    val type: String? = null,
    /** Discover-sourced only: the real portal category id. */
    val category: LenientStr? = null,
    /** "movie_<id>" | "series_<id>" when known. */
    val catalogId: String? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("screenshot_uri") val screenshotUri: String? = null,
    @SerialName("is_series") val isSeries: LenientBool? = null,
    @SerialName("is_season") val isSeason: LenientBool? = null,
    @SerialName("is_episode") val isEpisode: LenientBool? = null,
    /** Season number on an episode (what movie-link's `series` param wants). */
    @SerialName("series_number") val seriesNumber: LenientInt? = null,
    @SerialName("episode_num") val episodeNum: LenientInt? = null,
    /** DO NOT PLAY. Untokenized, may contain upstream credentials. */
    val cmd: String? = null,
    val number: LenientInt? = null,
    @SerialName("tv_genre_id") val tvGenreId: LenientStr? = null,
    @SerialName("category_id") val categoryId: LenientStr? = null,
    @SerialName("series_id") val seriesId: LenientStr? = null,
    @SerialName("season_id") val seasonId: LenientStr? = null,
    @SerialName("stream_id") val streamId: LenientStr? = null,
    /** DO NOT USE for display; raw upstream URL. */
    @SerialName("stream_icon") val streamIcon: String? = null,
    /** DO NOT USE for display; raw upstream URL. */
    val cover: String? = null,
    @SerialName("container_extension") val containerExtension: String? = null,
    /** Minutes, on Xtream episodes/details. */
    val runtime: LenientInt? = null,
    /** Episode length in minutes on Xtream episode rows; other shapes elsewhere. */
    val time: LenientStr? = null,
    /** Free-form on list items ("01:32:00" or minutes). Display only. */
    val duration: LenientStr? = null,
    val description: String? = null,
    val plot: String? = null,
    val director: String? = null,
    val actors: String? = null,
    val cast: String? = null,
    val year: LenientStr? = null,
    val added: LenientStr? = null,
    val rating: LenientStr? = null,
    @SerialName("rating_imdb") val ratingImdb: LenientStr? = null,
    @SerialName("rating_kinopoisk") val ratingKinopoisk: LenientStr? = null,
    @SerialName("rating_mpaa") val ratingMpaa: String? = null,
    val age: LenientStr? = null,
    val country: String? = null,
    @SerialName("genres_str") val genresStr: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("backdrop_hd_path") val backdropHdPath: String? = null,
    @SerialName("trailer_key") val trailerKey: String? = null,
    /** Discover variants only. */
    val variantLabel: String? = null,
    /** Measured video quality from the server (servers from 2026-10-19), when someone has played it. */
    val video: VideoInfo? = null,
    val isPortal: Boolean? = null,
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: name ?: ""
    val overview: String? get() = (description ?: plot)?.takeUnless { it.isBlank() || it == "-" || it == "No description available" }
    val displayYear: String? get() = year?.takeUnless { it.isBlank() || it == "-" || it == "0" }
    val displayRating: String? get() = ratingImdb?.takeUnless { it.isBlank() || it == "-" || it == "0" } ?: rating?.takeUnless { it.isBlank() || it == "-" || it == "0" }
    val displayGenres: String? get() = genresStr?.takeUnless { it.isBlank() || it == "-" }

    /** Raw id without the Discover `movie_`/`series_` prefix. */
    val rawId: String get() = id.removePrefix("movie_").removePrefix("series_")
    val discoverType: String? get() = type ?: when {
        id.startsWith("movie_") -> "movie"
        id.startsWith("series_") -> "series"
        else -> null
    }

    /** What to send as movie-link `category` for the admin label: `genres_str || tv_genre_id || category_id`. */
    val labelCategory: String? get() = genresStr?.takeIf { it.isNotBlank() } ?: tvGenreId?.takeIf { it.isNotBlank() } ?: categoryId?.takeIf { it.isNotBlank() }
}

/**
 * What a player actually decoded for this title (server `video`): the real resolution
 * beats quality words in the portal name ("HDCam" on a clean 1080p file).
 */
@Serializable
data class VideoInfo(
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val codec: String? = null,
    /** "2160p" / "1080p" / "720p" / "576p" / "480p" / "SD". */
    val label: String? = null,
)

/** Body of POST /api/v2/media-info. */
@Serializable
data class MediaInfoReport(
    val itemType: String,
    val itemId: String,
    val width: Int,
    val height: Int,
    val fps: Double? = null,
    val codec: String? = null,
)

/** One OpenSubtitles result from GET /api/v2/subtitles/search. */
@Serializable
data class SubtitleResult(
    val id: LenientStr? = null,
    val fileId: Long = 0,
    val language: String? = null,
    val releaseName: String? = null,
    val downloadCount: Int = 0,
)

@Serializable
data class SubtitleSearchResponse(val success: Boolean = false, val results: List<SubtitleResult> = emptyList())
