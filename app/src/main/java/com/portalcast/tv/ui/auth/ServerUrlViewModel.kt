package com.portalcast.tv.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.repo.AuthRepository
import com.portalcast.tv.ui.components.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ServerUrlViewModel(private val auth: AuthRepository) : ViewModel() {

    data class State(
        val input: String = "",
        val busy: Boolean = false,
        val error: String? = null,
        /** One-shot: the screen navigates to pairing and calls [consumeConnected]. */
        val connected: Boolean = false,
    )

    private val _state = MutableStateFlow(State(input = auth.serverUrl.value.orEmpty()))
    val state: StateFlow<State> = _state

    fun onInputChange(value: String) = _state.update { it.copy(input = value, error = null) }

    fun connect() {
        val input = _state.value.input.trim()
        if (_state.value.busy) return
        if (input.isEmpty()) {
            _state.update { it.copy(error = "Enter the server address.") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            auth.setServerUrl(input).fold(
                onSuccess = { _state.update { it.copy(busy = false, connected = true) } },
                onFailure = { e -> _state.update { it.copy(busy = false, error = messageFor(e, input)) } },
            )
        }
    }

    fun consumeConnected() = _state.update { it.copy(connected = false) }

    private fun messageFor(e: Throwable, input: String): String = when (e) {
        is ApiError.NoServer -> "\"$input\" is not a valid address. Use host:port (192.168.1.10:3000) or a full URL (https://iptv.example.com)."
        is ApiError.Network -> "Can't reach $input. Check the address and that the server is running." +
            (e.cause?.message?.takeIf { it.isNotBlank() }?.let { "\n($it)" } ?: "")
        else -> e.userMessage()
    }
}
