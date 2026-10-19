package com.portalcast.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.portalcast.tv.data.model.OpenSubtitlesLinkRequest
import com.portalcast.tv.data.model.SubdlKeyRequest
import com.portalcast.tv.data.model.SubtitleAccountStatus
import com.portalcast.tv.data.model.UserDto
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.net.ApiService
import com.portalcast.tv.data.repo.AuthRepository
import com.portalcast.tv.data.store.LocalPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Subtitle accounts" section: the user's own OpenSubtitles login/key and SubDL key. */
data class SubtitleAccountsUi(
    val status: SubtitleAccountStatus? = null,
    val osUsername: String = "",
    val osPassword: String = "",
    val osApiKey: String = "",
    val subdlKey: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    /** The server is too old for subtitle accounts (404). */
    val unsupported: Boolean = false,
)

class SettingsViewModel(
    private val auth: AuthRepository,
    private val prefs: LocalPrefs,
    private val api: ApiService? = null,
) : ViewModel() {

    val serverUrl: StateFlow<String?> = auth.serverUrl

    val user: StateFlow<UserDto?> = auth.session
        .map { it?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, auth.session.value?.user)

    /** Mirrors [LocalPrefs.directLiveStreams]; false until DataStore has been read. */
    val directLiveStreams: StateFlow<Boolean> = prefs.directLiveStreams
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setDirectLiveStreams(direct: Boolean) {
        viewModelScope.launch { prefs.setDirectLiveStreams(direct) }
    }

    fun toggleDirectLiveStreams() = setDirectLiveStreams(!directLiveStreams.value)

    /** Clears the stored session; the nav gate returns to pairing. */
    fun signOut() {
        viewModelScope.launch { auth.logout() }
    }

    // ---- subtitle accounts --------------------------------------------------------------
    private val _subs = MutableStateFlow(SubtitleAccountsUi())
    val subtitleAccounts: StateFlow<SubtitleAccountsUi> = _subs.asStateFlow()

    init { refreshSubtitleAccounts() }

    fun refreshSubtitleAccounts() {
        val api = api ?: return
        viewModelScope.launch {
            runCatching { api.subtitleAccounts() }
                .onSuccess { st -> _subs.update { it.copy(status = st, unsupported = false) } }
                .onFailure { e -> if ((e as? retrofit2.HttpException)?.code() == 404) _subs.update { it.copy(unsupported = true) } }
        }
    }

    fun setOsUsername(v: String) = _subs.update { it.copy(osUsername = v) }
    fun setOsPassword(v: String) = _subs.update { it.copy(osPassword = v) }
    fun setOsApiKey(v: String) = _subs.update { it.copy(osApiKey = v) }
    fun setSubdlKey(v: String) = _subs.update { it.copy(subdlKey = v) }

    private fun run(done: String, block: suspend (ApiService) -> Unit) {
        val api = api ?: return
        if (_subs.value.busy) return
        _subs.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val r = runCatching { block(api) }
            _subs.update { st ->
                r.fold(
                    onSuccess = { st.copy(busy = false, message = done, osPassword = "", osApiKey = "", subdlKey = "") },
                    onFailure = { st.copy(busy = false, message = ApiError.from(it).message ?: "Something went wrong") },
                )
            }
            if (r.isSuccess) refreshSubtitleAccounts()
        }
    }

    fun saveOpenSubtitles() {
        val s = _subs.value
        val user = s.osUsername.trim().takeIf { it.isNotEmpty() }
        val pass = s.osPassword.takeIf { it.isNotEmpty() }
        val key = s.osApiKey.trim().takeIf { it.isNotEmpty() }
        if (user == null && key == null) { _subs.update { it.copy(message = "Enter your OpenSubtitles login or API key.") }; return }
        run("OpenSubtitles saved.") { it.linkOpenSubtitles(OpenSubtitlesLinkRequest(user, pass, key)).close() }
    }

    fun removeOpenSubtitles() = run("OpenSubtitles removed.") { it.unlinkOpenSubtitles().close() }

    fun saveSubdl() {
        val key = _subs.value.subdlKey.trim()
        if (key.isEmpty()) { _subs.update { it.copy(message = "Enter your SubDL API key.") }; return }
        run("SubDL key saved.") { it.setSubdlKey(SubdlKeyRequest(key)).close() }
    }

    fun removeSubdl() = run("SubDL key removed.") { it.removeSubdlKey().close() }
}
