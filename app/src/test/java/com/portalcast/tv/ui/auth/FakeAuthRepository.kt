package com.portalcast.tv.ui.auth

import com.portalcast.tv.data.model.DeviceCodeResponse
import com.portalcast.tv.data.model.DevicePollResponse
import com.portalcast.tv.data.model.UserDto
import com.portalcast.tv.data.repo.AuthRepository
import com.portalcast.tv.data.store.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Scripted [AuthRepository] for the auth ViewModels. Poll replies are consumed from
 * [pollResults] in order; once it is empty every poll answers `pending`.
 */
class FakeAuthRepository : AuthRepository {
    override val isLoaded = MutableStateFlow(true)
    override val serverUrl = MutableStateFlow<String?>("http://192.168.1.10:3000")
    override val session = MutableStateFlow<Session?>(null)

    var codeRequests = 0
        private set
    var codeResult: Result<DeviceCodeResponse>? = null

    val polledCodes = mutableListOf<String>()
    val polls: Int get() = polledCodes.size
    var pollResults = ArrayDeque<Result<DevicePollResponse>>()
    /** How long each poll "takes" (virtual time), to prove the loop never overlaps calls. */
    var pollDurationMs = 0L
    var pollsInFlight = 0
        private set
    var maxPollsInFlight = 0
        private set

    var acceptCalls = 0
        private set

    override suspend fun setServerUrl(input: String): Result<Unit> {
        serverUrl.value = input
        return Result.success(Unit)
    }

    override suspend fun requestDeviceCode(): Result<DeviceCodeResponse> {
        codeRequests++
        return codeResult ?: Result.success(
            DeviceCodeResponse(
                deviceCode = "device-$codeRequests",
                userCode = "ABC-DE$codeRequests",
                verificationUrl = "http://192.168.1.10:3000/#/verify?code=ABC-DE$codeRequests",
                expiresIn = 300,
            ),
        )
    }

    override suspend fun pollDeviceCode(deviceCode: String): Result<DevicePollResponse> {
        polledCodes += deviceCode
        pollsInFlight++
        maxPollsInFlight = maxOf(maxPollsInFlight, pollsInFlight)
        try {
            if (pollDurationMs > 0) delay(pollDurationMs)
        } finally {
            pollsInFlight--
        }
        return pollResults.removeFirstOrNull() ?: Result.success(DevicePollResponse(status = "pending"))
    }

    override suspend fun acceptTokens(accessToken: String, refreshToken: String, user: UserDto) {
        acceptCalls++
        session.value = Session(accessToken, refreshToken, 0L, user)
    }

    override suspend fun loginWithPassword(email: String, password: String): Result<Unit> = error("not scripted")
    override suspend fun refreshProfile(): Result<UserDto> = error("not scripted")
    override suspend fun logout() {
        session.value = null
    }

    companion object {
        fun pending() = Result.success(DevicePollResponse(status = "pending"))
        fun expired() = Result.success(DevicePollResponse(status = "expired"))
        fun authorized(user: UserDto = UserDto(id = 7, email = "tv@example.com", name = "TV")) = Result.success(
            DevicePollResponse(status = "authorized", accessToken = "access", refreshToken = "refresh", user = user),
        )
    }
}
