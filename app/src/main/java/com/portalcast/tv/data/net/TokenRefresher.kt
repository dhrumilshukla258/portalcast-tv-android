package com.portalcast.tv.data.net

import com.portalcast.tv.data.model.RefreshRequest
import com.portalcast.tv.data.model.RefreshResponse
import com.portalcast.tv.data.store.SessionStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Single-flight refresh of the access token, callable from OkHttp's synchronous
 * interceptor/authenticator threads. Uses a bare client (no auth interceptor) so it
 * can never recurse.
 *
 * Semantics (contract §2.2): the server returns the same refresh token; 401/400
 * means the refresh token is dead and the session is cleared (UI then returns to
 * pairing). A network error leaves the session intact and returns null so the
 * caller fails just this request.
 */
class TokenRefresher(
    private val sessionStore: SessionStore,
    private val bareClient: OkHttpClient,
) {
    private val lock = Any()

    /**
     * @param staleAccessToken the token the caller tried with; if the stored token already
     *   differs, another thread refreshed first and that token is returned without a call.
     * @return a usable access token, or null if refresh was impossible.
     */
    fun refresh(staleAccessToken: String?): String? = synchronized(lock) {
        val session = sessionStore.session.value ?: return null
        if (staleAccessToken != null && session.accessToken != staleAccessToken) return session.accessToken

        val base = sessionStore.serverUrl.value?.let { DynamicBaseUrlInterceptor.normalize(it) } ?: return null
        val url = base.newBuilder().encodedPath(base.encodedPath.trimEnd('/') + "/api/auth/refresh").build()
        val body = AppJson.encodeToString(RefreshRequest(session.refreshToken)).toRequestBody(JSON)
        val request = Request.Builder().url(url).post(body).header("User-Agent", UserAgent.value).build()

        try {
            bareClient.newCall(request).execute().use { resp ->
                if (resp.code == 401 || resp.code == 400) {
                    runBlocking { sessionStore.clearSession() }
                    return null
                }
                if (!resp.isSuccessful) return null
                val parsed = AppJson.decodeFromString(RefreshResponse.serializer(), resp.body?.string().orEmpty())
                runBlocking { sessionStore.updateTokens(parsed.accessToken, parsed.refreshToken ?: session.refreshToken) }
                parsed.accessToken
            }
        } catch (e: IOException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
