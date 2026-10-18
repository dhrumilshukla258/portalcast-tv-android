package com.portalcast.tv.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.portalcast.tv.data.model.UserDto
import com.portalcast.tv.data.net.AppJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/** What a signed-in device holds. Tokens are stored in plain DataStore (plan decision #8). */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** ms epoch when the refresh token was issued; it dies 180 days later (no sliding). */
    val refreshIssuedAt: Long,
    val user: UserDto,
)

/**
 * Persists server URL + session and mirrors them into StateFlows that the
 * network layer reads synchronously. [isLoaded] flips true after the first read
 * so the nav host can wait before choosing a start destination.
 */
class SessionStore(context: Context, scope: CoroutineScope) {
    private val store = context.sessionDataStore

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded

    init {
        scope.launch {
            val prefs = store.data.first()
            _serverUrl.value = prefs[KEY_SERVER_URL]
            _session.value = readSession(prefs)
            _isLoaded.value = true
        }
    }

    suspend fun setServerUrl(url: String) {
        _serverUrl.value = url
        store.edit { it[KEY_SERVER_URL] = url }
    }

    suspend fun saveSession(accessToken: String, refreshToken: String, user: UserDto) {
        val s = Session(accessToken, refreshToken, System.currentTimeMillis(), user)
        _session.value = s
        store.edit {
            it[KEY_ACCESS] = accessToken
            it[KEY_REFRESH] = refreshToken
            it[KEY_REFRESH_ISSUED] = s.refreshIssuedAt
            it[KEY_USER] = AppJson.encodeToString(user)
        }
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        val cur = _session.value ?: return
        _session.value = cur.copy(accessToken = accessToken, refreshToken = refreshToken)
        store.edit {
            it[KEY_ACCESS] = accessToken
            it[KEY_REFRESH] = refreshToken
        }
    }

    suspend fun updateUser(user: UserDto) {
        val cur = _session.value ?: return
        _session.value = cur.copy(user = user)
        store.edit { it[KEY_USER] = AppJson.encodeToString(user) }
    }

    /** Logout. Keeps the server URL so the next pairing does not re-ask for it. */
    suspend fun clearSession() {
        _session.value = null
        store.edit {
            it.remove(KEY_ACCESS); it.remove(KEY_REFRESH); it.remove(KEY_REFRESH_ISSUED); it.remove(KEY_USER)
        }
    }

    private fun readSession(prefs: Preferences): Session? {
        val access = prefs[KEY_ACCESS] ?: return null
        val refresh = prefs[KEY_REFRESH] ?: return null
        val user = prefs[KEY_USER]?.let { runCatching { AppJson.decodeFromString(UserDto.serializer(), it) }.getOrNull() } ?: return null
        return Session(access, refresh, prefs[KEY_REFRESH_ISSUED] ?: 0L, user)
    }

    private companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_ACCESS = stringPreferencesKey("access_token")
        val KEY_REFRESH = stringPreferencesKey("refresh_token")
        val KEY_REFRESH_ISSUED = longPreferencesKey("refresh_issued_at")
        val KEY_USER = stringPreferencesKey("user_json")
    }
}
