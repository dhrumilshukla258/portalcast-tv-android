package com.portalcast.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.portalcast.tv.data.model.UserDto
import com.portalcast.tv.data.repo.AuthRepository
import com.portalcast.tv.data.store.LocalPrefs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val auth: AuthRepository, private val prefs: LocalPrefs) : ViewModel() {

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
}
