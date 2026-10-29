package com.portalcast.tv.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.localPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "local_prefs")

/** Device-only UI state. Anything the user would expect on their other devices goes to the server preferences instead. */
class LocalPrefs(context: Context) {
    private val store = context.localPrefsDataStore

    val lastTab: Flow<String?> = store.data.map { it[KEY_LAST_TAB] }
    /** false = `proxy=1` via the server (default); true = ask the server for a direct CDN redirect. */
    val directLiveStreams: Flow<Boolean> = store.data.map { it[KEY_DIRECT_LIVE] ?: false }
    /** Settings › Player: "" = built-in, "ask" = Android's chooser, else an app's package (playback/ExternalPlayer.kt). */
    val externalPlayer: Flow<String> = store.data.map { it[KEY_EXTERNAL_PLAYER] ?: "" }

    suspend fun setLastTab(tab: String) = store.edit { it[KEY_LAST_TAB] = tab }
    suspend fun setDirectLiveStreams(direct: Boolean) = store.edit { it[KEY_DIRECT_LIVE] = direct }
    suspend fun setExternalPlayer(choice: String) = store.edit { it[KEY_EXTERNAL_PLAYER] = choice }

    private companion object {
        val KEY_LAST_TAB = stringPreferencesKey("last_tab")
        val KEY_DIRECT_LIVE = booleanPreferencesKey("direct_live")
        val KEY_EXTERNAL_PLAYER = stringPreferencesKey("external_player")
    }
}
