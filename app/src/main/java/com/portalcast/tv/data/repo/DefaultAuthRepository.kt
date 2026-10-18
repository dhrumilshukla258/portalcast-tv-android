package com.portalcast.tv.data.repo

import com.portalcast.tv.data.model.DeviceCodeResponse
import com.portalcast.tv.data.model.DevicePollRequest
import com.portalcast.tv.data.model.DevicePollResponse
import com.portalcast.tv.data.model.LoginRequest
import com.portalcast.tv.data.model.UserDto
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.net.ApiService
import com.portalcast.tv.data.net.DynamicBaseUrlInterceptor
import com.portalcast.tv.data.net.UserAgent
import com.portalcast.tv.data.net.apiCall
import com.portalcast.tv.data.store.Session
import com.portalcast.tv.data.store.SessionSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Server URL, pairing, password login and profile refresh (contract §2).
 *
 * @param probeClient bare client for the reachability probe in [setServerUrl]; it
 *   must not carry the auth interceptors because no server is configured yet.
 */
class DefaultAuthRepository(
    private val api: ApiService,
    private val sessionStore: SessionSource,
    private val probeClient: OkHttpClient = OkHttpClient(),
) : AuthRepository {
    override val isLoaded: StateFlow<Boolean> get() = sessionStore.isLoaded
    override val serverUrl: StateFlow<String?> get() = sessionStore.serverUrl
    override val session: StateFlow<Session?> get() = sessionStore.session

    /**
     * Normalises [input], GETs `api/v2/provider-key` without auth and treats ANY HTTP
     * status (401 included) as "reachable"; only a transport failure is an error.
     * Switching to a different origin drops the current session, whose tokens
     * belong to the old server.
     */
    override suspend fun setServerUrl(input: String): Result<Unit> {
        val base = DynamicBaseUrlInterceptor.normalize(input) ?: return Result.failure(ApiError.NoServer())
        val probeUrl = base.newBuilder().encodedPath(base.encodedPath.trimEnd('/') + ApiPaths.PROVIDER_KEY).build()
        val request = Request.Builder().url(probeUrl).header("User-Agent", UserAgent.value).build()

        val probe = withContext(Dispatchers.IO) {
            try {
                Result.success(probeClient.newCall(request).execute().use { it.code })
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Result.failure(ApiError.from(t))
            }
        }
        if (probe.isFailure) return Result.failure(probe.exceptionOrNull()!!)

        val origin = base.toString().trimEnd('/')
        val previous = sessionStore.serverUrl.value?.let { DynamicBaseUrlInterceptor.normalize(it)?.toString()?.trimEnd('/') }
        if (previous != null && previous != origin && sessionStore.session.value != null) sessionStore.clearSession()
        sessionStore.setServerUrl(origin)
        return Result.success(Unit)
    }

    override suspend fun requestDeviceCode(): Result<DeviceCodeResponse> =
        apiCall(ApiPaths.DEVICE_CODE) { api.deviceCode() }

    /**
     * One poll. On `authorized` the session is persisted here as well, because the
     * server deletes the pairing row on that response and a lost reply means pairing again.
     */
    override suspend fun pollDeviceCode(deviceCode: String): Result<DevicePollResponse> {
        val result = apiCall(ApiPaths.DEVICE_POLL) { api.devicePoll(DevicePollRequest(deviceCode)) }
        val poll = result.getOrNull()
        if (poll != null && poll.isAuthorized) {
            sessionStore.saveSession(poll.accessToken!!, poll.refreshToken!!, poll.user ?: UserDto())
        }
        return result
    }

    override suspend fun acceptTokens(accessToken: String, refreshToken: String, user: UserDto) =
        sessionStore.saveSession(accessToken, refreshToken, user)

    /** Never auto-retried: the login route is rate limited to 5 per 5 minutes. */
    override suspend fun loginWithPassword(email: String, password: String): Result<Unit> {
        val result = apiCall(ApiPaths.LOGIN) { api.login(LoginRequest(email = email.trim(), password = password)) }
        val auth = result.getOrElse { return Result.failure(it) }
        sessionStore.saveSession(auth.accessToken, auth.refreshToken, auth.user)
        return Result.success(Unit)
    }

    /** 401 here means the refresh already failed (or never ran): the session is gone, go pair. */
    override suspend fun refreshProfile(): Result<UserDto> {
        val result = apiCall(ApiPaths.PROFILE) { api.profile().toUser() }
        result.onSuccess { sessionStore.updateUser(it) }
        if (result.exceptionOrNull() is ApiError.Unauthorized) sessionStore.clearSession()
        return result
    }

    override suspend fun logout() = sessionStore.clearSession()
}
