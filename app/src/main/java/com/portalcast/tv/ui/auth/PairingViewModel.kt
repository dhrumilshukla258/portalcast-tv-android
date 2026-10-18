package com.portalcast.tv.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.portalcast.tv.data.model.DeviceCodeResponse
import com.portalcast.tv.data.model.UserDto
import com.portalcast.tv.data.repo.AuthRepository
import com.portalcast.tv.ui.components.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class PairingState {
    /** Requesting a device code. */
    object Loading : PairingState()
    /** Code on screen, polling in progress. */
    data class Showing(val code: DeviceCodeResponse) : PairingState()
    /** The server reported the code consumed or past its 5 minutes; wait for "Generate new code". */
    object Expired : PairingState()
    /** Request or poll failed (network, 403 user inactive, ...). Wait for "Generate new code". */
    data class Failed(val message: String) : PairingState()
    /** Tokens persisted; the nav gate takes it from here. */
    object Authorized : PairingState()
}

/**
 * Device-code pairing (contract §2.3). Owns the 4 s poll cadence as a plain loop with
 * `delay`, so there is never more than one poll in flight, and stops the instant the
 * server says `authorized` or `expired`. Navigation is not this class's job: it
 * persists the session via [AuthRepository.acceptTokens] and the session gate moves on.
 */
class PairingViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow<PairingState>(PairingState.Loading)
    val state: StateFlow<PairingState> = _state

    private var job: Job? = null

    /**
     * Screen entered composition. Resumes polling a code that is already on screen
     * (e.g. back from the password screen), requests one if there is none yet, and
     * leaves Expired/Failed alone until the user asks for a new code.
     */
    fun onScreenEntered() {
        if (job?.isActive == true) return
        when (val current = _state.value) {
            is PairingState.Showing -> startPolling(current.code.deviceCode)
            PairingState.Loading -> regenerate()
            else -> Unit
        }
    }

    /** Screen left composition: stop polling (the code stays valid server-side). */
    fun onScreenLeft() {
        job?.cancel()
        job = null
    }

    /** Request a fresh code and start polling it. Also the "Generate new code" action. */
    fun regenerate() {
        job?.cancel()
        _state.value = PairingState.Loading
        job = viewModelScope.launch {
            val code = auth.requestDeviceCode().getOrElse { e ->
                _state.value = PairingState.Failed(e.userMessage())
                return@launch
            }
            _state.value = PairingState.Showing(code)
            pollLoop(code.deviceCode)
        }
    }

    private fun startPolling(deviceCode: String) {
        job?.cancel()
        job = viewModelScope.launch { pollLoop(deviceCode) }
    }

    private suspend fun pollLoop(deviceCode: String) {
        while (true) {
            delay(POLL_INTERVAL_MS)
            val poll = auth.pollDeviceCode(deviceCode).getOrElse { e ->
                _state.value = PairingState.Failed(e.userMessage())
                return
            }
            when {
                poll.isAuthorized -> {
                    // Server deletes the row on this response; a second poll would read "expired".
                    auth.acceptTokens(poll.accessToken!!, poll.refreshToken!!, poll.user ?: UserDto())
                    _state.value = PairingState.Authorized
                    return
                }
                poll.isExpired -> {
                    _state.value = PairingState.Expired
                    return
                }
                poll.isPending -> Unit
                else -> {
                    _state.value = PairingState.Failed(poll.error ?: "Unexpected reply from the server (${poll.status}).")
                    return
                }
            }
        }
    }

    companion object {
        const val POLL_INTERVAL_MS = 4_000L
    }
}
