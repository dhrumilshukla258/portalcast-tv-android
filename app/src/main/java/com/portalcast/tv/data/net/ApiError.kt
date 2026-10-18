package com.portalcast.tv.data.net

import com.portalcast.tv.data.model.ErrorBody
import retrofit2.HttpException
import java.io.IOException

/**
 * Typed failures for repository `Result`s. Map any throwable from a Retrofit call
 * with [from] so UI code can `when` on the cause instead of parsing messages.
 */
sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** No server configured yet, or the URL could not be parsed. */
    class NoServer : ApiError("No server configured")

    /** DNS/connect/timeout/TLS. Session is intact; retry is reasonable. */
    class Network(cause: Throwable) : ApiError(cause.message ?: "Network error", cause)

    /** 401 after a refresh attempt (or on an auth route). The session has been cleared. */
    class Unauthorized(message: String = "Unauthorized") : ApiError(message)

    /** 403 that is not a cold cache: disabled user, pending approval, admin-only route. */
    class Forbidden(message: String) : ApiError(message)

    /**
     * The four list routes 302 to admin-only `refresh-*` routes when the server
     * cache is empty. With redirects disabled we see the 302; a non-admin following
     * it would see 403. Either way: "library not synced yet, ask the admin".
     */
    class ColdCache : ApiError("Server library not synced yet")

    /** 429 on the auth or live buckets. */
    class RateLimited : ApiError("Too many requests, slow down")

    /** Any other non-2xx. */
    class Http(val code: Int, message: String) : ApiError(message)

    /** 2xx but the body did not contain what we needed (e.g. no `cmd` in movie-link). */
    class Malformed(message: String) : ApiError(message)

    companion object {
        private val coldCachePaths = listOf("/api/v2/groups", "/api/v2/channels", "/api/v2/movie-groups", "/api/v2/series-groups")

        fun from(t: Throwable, requestPath: String? = null): ApiError = when (t) {
            is ApiError -> t
            is HttpException -> {
                val body = runCatching { t.response()?.errorBody()?.string() }.getOrNull()
                val msg = body?.let { runCatching { AppJson.decodeFromString(ErrorBody.serializer(), it).error }.getOrNull() }
                    ?: t.message() ?: "HTTP ${t.code()}"
                val isListRoute = requestPath != null && coldCachePaths.any { requestPath.endsWith(it) }
                when (t.code()) {
                    301, 302, 303, 307, 308 -> if (isListRoute) ColdCache() else Http(t.code(), msg)
                    401 -> Unauthorized(msg)
                    403 -> if (isListRoute) ColdCache() else Forbidden(msg)
                    429 -> RateLimited()
                    else -> Http(t.code(), msg)
                }
            }
            is NoServerConfiguredException -> NoServer()
            is IOException -> Network(t)
            else -> Http(0, t.message ?: t.javaClass.simpleName)
        }
    }
}

/**
 * Thrown by [DynamicBaseUrlInterceptor] when no server URL is stored. It must be an
 * [IOException]: OkHttp passes those through to the caller unchanged, whereas any
 * other throwable from an interceptor is replaced by an opaque
 * `IOException("canceled due to ...")`. [ApiError.from] maps it to [ApiError.NoServer].
 */
class NoServerConfiguredException : IOException("No server configured")

/** `runCatching` for API calls that normalises every failure into an [ApiError]. */
suspend inline fun <T> apiCall(path: String? = null, crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        Result.failure(ApiError.from(t, path))
    }
