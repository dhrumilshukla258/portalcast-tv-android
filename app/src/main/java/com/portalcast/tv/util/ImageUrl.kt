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

    /** Resolve a relative stream `cmd` such as `/api/proxy?t=…` against the server origin. */
    fun absolute(serverUrl: String?, cmdOrUrl: String?): String? {
        val c = cmdOrUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (c.startsWith("http://") || c.startsWith("https://")) return c
        val base = serverUrl?.let { DynamicBaseUrlInterceptor.normalize(it) } ?: return null
        val origin = base.toString().trimEnd('/')
        return origin + (if (c.startsWith("/")) c else "/$c")
    }
}
