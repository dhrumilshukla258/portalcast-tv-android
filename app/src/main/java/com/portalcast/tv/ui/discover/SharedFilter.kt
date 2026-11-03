package com.portalcast.tv.ui.discover

import com.portalcast.tv.data.model.DiscoverQuery

/**
 * The filters picked on Home, Movies or Series, kept when switching between them (owner
 * 2026-11-03: Movies with filters X, Y, Z, then Series: the filters stay). Each tab has its own
 * [DiscoverViewModel]; a tab coming on screen takes the filters set last on any of them.
 *
 * TMDB names some genres differently for series ("Action & Adventure", "Sci-Fi & Fantasy",
 * "War & Politics"), so genres are carried over by their counterpart. Channels (networks) are a
 * series thing and are not carried to movies.
 */
object SharedFilter {
    /** The last filters set by the viewer (null = none), and a counter of changes. */
    @Volatile var query: DiscoverQuery? = null
        private set
    @Volatile var version: Int = 0
        private set

    fun set(q: DiscoverQuery?) {
        query = q
        version++
    }

    private val toSeries = mapOf(
        "Action" to "Action & Adventure",
        "Adventure" to "Action & Adventure",
        "Science Fiction" to "Sci-Fi & Fantasy",
        "Fantasy" to "Sci-Fi & Fantasy",
        "War" to "War & Politics",
    )
    private val toMovie = mapOf(
        "Action & Adventure" to "Action",
        "Sci-Fi & Fantasy" to "Science Fiction",
        "War & Politics" to "War",
    )

    /** [q] for a tab showing [type] ("movie", "series", or null for both). */
    fun forType(q: DiscoverQuery, type: String?): DiscoverQuery {
        val map = when (type) {
            "series" -> toSeries
            "movie" -> toMovie
            else -> emptyMap()
        }
        fun conv(list: List<String>) = list.map { map[it] ?: it }.distinct()
        val genres = conv(q.genres)
        return q.copy(
            type = type,
            genres = genres,
            notGenres = conv(q.notGenres).filterNot { it in genres },
            network = if (type == "movie") null else q.network,
        )
    }
}
