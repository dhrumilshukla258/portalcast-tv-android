package com.stalker.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stalker.tv.data.ApiClient
import com.stalker.tv.data.Credentials
import com.stalker.tv.data.CredentialStore
import com.stalker.tv.data.LiveStream
import com.stalker.tv.data.XtreamApi
import com.stalker.tv.ui.browse.CategoryRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class Screen {
    object Login : Screen()
    object Browse : Screen()
    data class Player(val url: String) : Screen()
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val credentialStore = CredentialStore(application)
    private var api: XtreamApi? = null
    private var credentials: Credentials? = null

    val screen = MutableStateFlow<Screen>(Screen.Login)
    val isLoading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val rows: MutableStateFlow<List<CategoryRow>> = MutableStateFlow(emptyList())

    init {
        viewModelScope.launch {
            credentialStore.credentials.collect { saved ->
                if (saved != null && screen.value is Screen.Login) {
                    login(saved.serverUrl, saved.username, saved.password, persist = false)
                }
            }
        }
    }

    fun login(serverUrl: String, username: String, password: String, persist: Boolean = true) {
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            try {
                val client = ApiClient.create(serverUrl)
                val response = client.login(username, password)
                if (response.userInfo?.auth != 1) {
                    error.value = "Login failed — check credentials"
                    isLoading.value = false
                    return@launch
                }
                api = client
                credentials = Credentials(serverUrl, username, password)
                if (persist) credentialStore.save(credentials!!)

                loadChannels(client, username, password)
                screen.value = Screen.Browse
            } catch (e: Exception) {
                error.value = "Could not reach server: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    private suspend fun loadChannels(client: XtreamApi, username: String, password: String) {
        val categories = client.getLiveCategories(username, password)
        val built = categories.mapNotNull { category ->
            val channels: List<LiveStream> = try {
                client.getLiveStreams(username, password, categoryId = category.categoryId)
            } catch (e: Exception) {
                emptyList()
            }
            if (channels.isEmpty()) null else CategoryRow(category, channels)
        }
        rows.value = built
    }

    fun playChannel(channel: LiveStream) {
        val creds = credentials ?: return
        val url = XtreamApi.streamUrl(creds.serverUrl, creds.username, creds.password, channel.streamId)
        screen.value = Screen.Player(url)
    }

    fun backToBrowse() {
        screen.value = Screen.Browse
    }

    fun signOut() {
        viewModelScope.launch {
            credentialStore.clear()
            api = null
            credentials = null
            rows.value = emptyList()
            screen.value = Screen.Login
        }
    }
}
