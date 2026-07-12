package com.portalcast.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "credentials")

data class Credentials(val serverUrl: String, val username: String, val password: String)

class CredentialStore(private val context: Context) {
    private val keyUrl = stringPreferencesKey("server_url")
    private val keyUser = stringPreferencesKey("username")
    private val keyPass = stringPreferencesKey("password")

    val credentials: Flow<Credentials?> = context.dataStore.data.map { prefs ->
        val url = prefs[keyUrl]
        val user = prefs[keyUser]
        val pass = prefs[keyPass]
        if (url != null && user != null && pass != null) Credentials(url, user, pass) else null
    }

    suspend fun save(creds: Credentials) {
        context.dataStore.edit { prefs ->
            prefs[keyUrl] = creds.serverUrl
            prefs[keyUser] = creds.username
            prefs[keyPass] = creds.password
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
