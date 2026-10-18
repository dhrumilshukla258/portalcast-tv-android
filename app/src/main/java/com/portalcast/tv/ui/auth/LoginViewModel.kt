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

/**
 * Email + password fallback (contract §2.4). One attempt per Sign in press, never an
 * automatic retry: the server allows 5 per 5 minutes per IP.
 */
class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    data class State(
        val email: String = "",
        val password: String = "",
        val busy: Boolean = false,
        val error: String? = null,
        /** Stays true after success so the button cannot fire again while the gate navigates. */
        val signedIn: Boolean = false,
    ) {
        val canSubmit: Boolean get() = !busy && !signedIn
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        val email = s.email.trim()
        if (email.isEmpty() || s.password.isEmpty()) {
            _state.update { it.copy(error = "Enter your email and password.") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            auth.loginWithPassword(email, s.password).fold(
                onSuccess = { _state.update { it.copy(busy = false, signedIn = true) } },
                onFailure = { e -> _state.update { it.copy(busy = false, error = messageFor(e)) } },
            )
        }
    }

    private fun messageFor(e: Throwable): String = when (e) {
        is ApiError.Unauthorized -> "Wrong email or password"
        is ApiError.Forbidden -> e.message?.takeIf { it.isNotBlank() } ?: "Your account can't sign in right now."
        is ApiError.RateLimited -> "Too many attempts, wait 5 minutes"
        else -> e.userMessage()
    }
}
