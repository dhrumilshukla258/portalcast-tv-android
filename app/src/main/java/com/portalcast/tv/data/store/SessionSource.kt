package com.portalcast.tv.data.store

import com.portalcast.tv.data.model.UserDto
import kotlinx.coroutines.flow.StateFlow

/**
 * What the network layer and repositories need from session persistence.
 * [SessionStore] is the DataStore-backed implementation used by the app; unit
 * tests substitute an in-memory one because DataStore needs an Android Context.
 */
interface SessionSource {
    /** True once the persisted state has been read at least once. */
    val isLoaded: StateFlow<Boolean>
    /** The user's server origin, e.g. `http://192.168.1.10:3000`, or null before first run. */
    val serverUrl: StateFlow<String?>
    val session: StateFlow<Session?>

    suspend fun setServerUrl(url: String)
    suspend fun saveSession(accessToken: String, refreshToken: String, user: UserDto)
    suspend fun updateTokens(accessToken: String, refreshToken: String)
    suspend fun updateUser(user: UserDto)
    /** Logout. Keeps the server URL so the next pairing does not re-ask for it. */
    suspend fun clearSession()
}
