package com.portalcast.tv.data.net

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Reactive refresh: on a 401 from a Bearer-authenticated request, refresh once
 * (single-flight via [TokenRefresher]) and retry once. Auth routes and second
 * failures give up, which surfaces as [ApiError.Unauthorized].
 */
class TokenAuthenticator(private val refresher: TokenRefresher) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        val header = request.header("Authorization") ?: return null
        if (request.url.encodedPath.contains("/api/auth/")) return null
        if (responseCount(response) >= 2) return null

        val stale = header.removePrefix("Bearer ").trim()
        val fresh = refresher.refresh(stale) ?: return null
        if (fresh == stale) return null
        return request.newBuilder().header("Authorization", "Bearer $fresh").build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
