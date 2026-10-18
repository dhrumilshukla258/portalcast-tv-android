package com.portalcast.tv.data.net

import com.portalcast.tv.data.store.SessionStore
import com.portalcast.tv.util.Jwt
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `User-Agent` to every request and `Authorization: Bearer` to everything
 * except the `/api/auth/` routes. Refreshes proactively when the access JWT expires within
 * [PROACTIVE_WINDOW_SECONDS] so a 1-hour token never expires mid-browse.
 * Reactive refresh on an actual 401 lives in [TokenAuthenticator].
 */
class AuthInterceptor(
    private val sessionStore: SessionStore,
    private val refresher: TokenRefresher,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder().header("User-Agent", UserAgent.value)

        val path = original.url.encodedPath
        if (!path.contains("/api/auth/")) {
            var token = sessionStore.session.value?.accessToken
            if (token != null && Jwt.expiresWithin(token, PROACTIVE_WINDOW_SECONDS)) {
                token = refresher.refresh(token) ?: token
            }
            if (token != null) builder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(builder.build())
    }

    companion object {
        const val PROACTIVE_WINDOW_SECONDS = 5 * 60L
    }
}
