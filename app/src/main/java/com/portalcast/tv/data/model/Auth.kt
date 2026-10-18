package com.portalcast.tv.data.model

import com.portalcast.tv.data.net.LenientInt
import kotlinx.serialization.Serializable

// ---- requests ---------------------------------------------------------------

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    /** Always "tv": that is what gives the 180-day refresh token (contract §2.4). */
    val clientType: String = "tv",
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class DevicePollRequest(val deviceCode: String)

// ---- responses --------------------------------------------------------------

@Serializable
data class UserDto(
    val id: LenientInt = 0,
    val email: String = "",
    val name: String? = null,
    val role: String? = null,
    val avatarUrl: String? = null,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
    val isAdmin: Boolean get() = role == "admin"
}

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

/** `POST /api/auth/refresh`: the refresh token is echoed back unchanged (no rotation). */
@Serializable
data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String? = null,
)

@Serializable
data class DeviceCodeResponse(
    val deviceCode: String,
    /** e.g. "ABC-DEF"; the server strips the dash and case on authorize. */
    val userCode: String,
    /** Hash route into the webui: `<origin>/#/verify?code=ABC-DEF`. Render it as a QR locally. */
    val verificationUrl: String,
    val expiresIn: LenientInt? = null,
)

@Serializable
data class DevicePollResponse(
    /** "pending" | "authorized" | "expired". There is no "denied". */
    val status: String = "expired",
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val user: UserDto? = null,
    val error: String? = null,
) {
    val isPending get() = status == "pending"
    val isAuthorized get() = status == "authorized" && accessToken != null && refreshToken != null
    val isExpired get() = status == "expired"
}

/** `GET /api/user/profile` */
@Serializable
data class ProfileResponse(
    val id: LenientInt = 0,
    val email: String = "",
    val name: String? = null,
    val role: String? = null,
    val avatarUrl: String? = null,
    val preferences: Preferences? = null,
) {
    fun toUser() = UserDto(id = id, email = email, name = name, role = role, avatarUrl = avatarUrl)
}
