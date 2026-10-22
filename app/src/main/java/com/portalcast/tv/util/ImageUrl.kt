package com.portalcast.tv.util

import com.portalcast.tv.data.net.DynamicBaseUrlInterceptor

/**
 * The one image URL rule (contract §4): absolute URLs load as-is (TMDB, or the
 * server's own `/api/images/proxy?u=`), relative portal paths go through the
 * unauthenticated `/api/images` relay on the user's server.
 */
object ImageUrl {
    fun resolve(serverUrl: String?, path: String?): String? {
        val p = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (p.startsWith("http://") || p.startsWith("https://")) return p
        val base = serverUrl?.let { DynamicBaseUrlInterceptor.normalize(it) } ?: return null
        val origin = base.toString().trimEnd('/')
        val rel = if (p.startsWith("/")) p else "/$p"
        return "$origin/api/images$rel"
    }

    /** TMDB size widths, for [downsize]. */
    private val TMDB_SIZE = Regex("""(/api/images/tmdb/|image\.tmdb\.org/t/p/)(w\d+|original)/""")

    /**
     * The same TMDB image at [target] width (`w342`, `w780`, `w1280`) when the URL asks for a
     * larger one (`w500`, `original`...): cards and backdrops get the smallest file that still
     * looks sharp (Phase 4). Never enlarges; other URLs (portal art, NAS art) are unchanged.
     */
    fun downsize(url: String?, target: String): String? {
        if (url == null) return null
        val m = TMDB_SIZE.find(url) ?: return url
        val current = m.groupValues[2]
        val have = if (current == "original") Int.MAX_VALUE else current.drop(1).toIntOrNull() ?: return url
        val want = target.drop(1).toIntOrNull() ?: return url
        if (have <= want) return url
        return url.replaceRange(m.groups[2]!!.range, target)
    }

    /** Resolve a relative stream `cmd` such as `/api/proxy?t=…` against the server origin. */
    fun absolute(serverUrl: String?, cmdOrUrl: String?): String? {
        val c = cmdOrUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (c.startsWith("http://") || c.startsWith("https://")) return c
        val base = serverUrl?.let { DynamicBaseUrlInterceptor.normalize(it) } ?: return null
        val origin = base.toString().trimEnd('/')
        return origin + (if (c.startsWith("/")) c else "/$c")
    }
}
