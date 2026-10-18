package com.portalcast.tv.data.net

import com.portalcast.tv.data.store.SessionSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retrofit is built with a placeholder base URL; this interceptor swaps in the
 * user's server origin (scheme, host, port, and any base path such as
 * `https://example.com/portalcast/`) for every request. Throws
 * [NoServerConfiguredException] (an IOException, so OkHttp surfaces it intact; it maps
 * to [ApiError.NoServer]) when nothing is configured, so the failure is typed, not a DNS error.
 */
class DynamicBaseUrlInterceptor(private val sessionStore: SessionSource) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base = sessionStore.serverUrl.value?.let { normalize(it) } ?: throw NoServerConfiguredException()
        val rebuilt = rebase(request.url, base)
        return chain.proceed(request.newBuilder().url(rebuilt).build())
    }

    companion object {
        /** Accepts "host", "host:3000", "http://host", "https://host/path/"; returns a parsed URL or null. */
        fun normalize(input: String): HttpUrl? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
            return withScheme.toHttpUrlOrNull()
        }

        /** Replace scheme/host/port of [requestUrl] with [base], prefixing the base path if any. */
        fun rebase(requestUrl: HttpUrl, base: HttpUrl): HttpUrl {
            val basePath = base.encodedPath.trimEnd('/')
            val path = basePath + requestUrl.encodedPath
            return base.newBuilder()
                .encodedPath(if (path.startsWith("/")) path else "/$path")
                .encodedQuery(requestUrl.encodedQuery)
                .build()
        }
    }
}
