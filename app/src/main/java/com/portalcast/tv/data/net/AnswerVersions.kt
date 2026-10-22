package com.portalcast.tv.data.net

import okhttp3.Request
import okhttp3.Response

/**
 * Answer versions (server Phase 3a). With its in-memory catalog on, the server sends
 * `X-Catalog-Version` and `X-Home-Version` on every answer. Catalog GETs then carry
 * `v=<version>`, and the server marks an answer to the current version as cached for a year:
 * a screen opened again is drawn from the 50 MB HTTP cache with no request at all. When the
 * version moves, the addresses change, so only screens opened again are fetched.
 *
 * The version comes from network answers only (cached ones are not read), and an answer
 * without the header (an older server, or the catalog switched off) clears it, so requests go
 * back to plain addresses and the usual caching. A title's own answer (seasons, episodes,
 * files: `movieId`, `seasonId`, `episodeId`) never carries `v`: it holds play links.
 */
object AnswerVersions {
    @Volatile var catalog: String? = null
        private set
    @Volatile var home: String? = null
        private set

    private val CATALOG_PATHS = Regex(
        "^/api/v2/(discover/(facets|details|variants|browse|rows|recommendations)|people/\\d+|search/people|movie-groups|series-groups|groups|movies|series)$",
    )
    private const val HOME_PATH = "/api/v2/home"
    private val TITLE_PARAMS = listOf("seasonId", "episodeId")

    /** From a network answer of the server. */
    fun report(response: Response) {
        if (!response.request.url.encodedPath.startsWith("/api/")) return
        catalog = response.header("X-Catalog-Version")
        home = response.header("X-Home-Version")
    }

    /** The request with `v` added when it is a catalog (or Home) read and a version is known. */
    fun pin(request: Request): Request {
        if (request.method != "GET") return request
        val url = request.url
        if (url.queryParameter("v") != null) return request
        val path = url.encodedPath
        val version = when {
            path == HOME_PATH -> home
            CATALOG_PATHS.matches(path) -> catalog
            else -> null
        } ?: return request
        val movieId = url.queryParameter("movieId")
        if ((movieId != null && movieId != "0") || TITLE_PARAMS.any { url.queryParameter(it) != null }) return request
        return request.newBuilder().url(url.newBuilder().addQueryParameter("v", version).build()).build()
    }
}
