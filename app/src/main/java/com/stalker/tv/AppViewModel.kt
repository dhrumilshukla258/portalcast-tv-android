package com.stalker.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stalker.tv.data.ApiClient
import com.stalker.tv.data.Credentials
import com.stalker.tv.data.CredentialStore
import com.stalker.tv.data.LiveStream
import com.stalker.tv.data.Series
import com.stalker.tv.data.VodStream
import com.stalker.tv.data.XtreamApi
import com.stalker.tv.ui.browse.CategoryRow
import com.stalker.tv.ui.browse.ContentTab
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
    val selectedTab = MutableStateFlow(ContentTab.LIVE)
    val liveRows: MutableStateFlow<List<CategoryRow<LiveStream>>> = MutableStateFlow(emptyList())
    val vodRows: MutableStateFlow<List<CategoryRow<VodStream>>> = MutableStateFlow(emptyList())
    val seriesRows: MutableStateFlow<List<CategoryRow<Series>>> = MutableStateFlow(emptyList())

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

                loadLive(client, username, password)
                screen.value = Screen.Browse
                loadVod(client, username, password)
                loadSeries(client, username, password)
            } catch (e: Exception) {
                error.value = "Could not reach server: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun selectTab(tab: ContentTab) {
        selectedTab.value = tab
    }

    private suspend fun loadLive(client: XtreamApi, username: String, password: String) {
        val categories = client.getLiveCategories(username, password)
        liveRows.value = categories.mapNotNull { category ->
            val channels: List<LiveStream> = try {
                client.getLiveStreams(username, password, categoryId = category.categoryId)
            } catch (e: Exception) {
                emptyList()
            }
            if (channels.isEmpty()) null else CategoryRow(category, channels)
        }
    }

    private suspend fun loadVod(client: XtreamApi, username: String, password: String) {
        try {
            val categories = client.getVodCategories(username, password)
            vodRows.value = categories.mapNotNull { category ->
                val movies: List<VodStream> = try {
                    client.getVodStreams(username, password, categoryId = category.categoryId)
                } catch (e: Exception) {
                    emptyList()
                }
                if (movies.isEmpty()) null else CategoryRow(category, movies)
            }
        } catch (e: Exception) {
            vodRows.value = emptyList()
        }
    }

    private suspend fun loadSeries(client: XtreamApi, username: String, password: String) {
        try {
            val categories = client.getSeriesCategories(username, password)
            seriesRows.value = categories.mapNotNull { category ->
                val series: List<Series> = try {
                    client.getSeries(username, password, categoryId = category.categoryId)
                } catch (e: Exception) {
                    emptyList()
                }
                if (series.isEmpty()) null else CategoryRow(category, series)
            }
        } catch (e: Exception) {
            seriesRows.value = emptyList()
        }
    }

    fun playChannel(channel: LiveStream) {
        val creds = credentials ?: return
        val url = XtreamApi.liveStreamUrl(creds.serverUrl, creds.username, creds.password, channel.streamId)
        screen.value = Screen.Player(url)
    }

    fun playVod(item: VodStream) {
        val creds = credentials ?: return
        val url = XtreamApi.vodStreamUrl(creds.serverUrl, creds.username, creds.password, item.streamId, item.containerExtension)
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
            liveRows.value = emptyList()
            vodRows.value = emptyList()
            seriesRows.value = emptyList()
            screen.value = Screen.Login
        }
    }
}
