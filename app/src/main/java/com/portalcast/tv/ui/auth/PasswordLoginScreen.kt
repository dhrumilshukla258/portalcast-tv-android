package com.portalcast.tv.ui.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.portalcast.tv.appContainer
import com.portalcast.tv.ui.components.ErrorText
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.components.TvButtonStyle
import com.portalcast.tv.ui.components.TvTextField

/**
 * Email + password sign-in. Success is not handled here: the session appears and the
 * nav gate moves to home. [onUsePairing] returns to the QR screen.
 */
@Composable
fun PasswordLoginScreen(onUsePairing: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm: LoginViewModel = viewModel(
        factory = viewModelFactory { initializer { LoginViewModel(container.authRepository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val emailFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { emailFocus.requestFocus() } }

    AuthFrame(
        title = "Sign in with email",
        subtitle = "Use the account you created on the Portalcast web app.",
    ) {
        TvTextField(
            value = state.email,
            onValueChange = vm::onEmailChange,
            label = "Email",
            hint = "you@example.com",
            enabled = state.canSubmit,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            focusRequester = emailFocus,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        TvTextField(
            value = state.password,
            onValueChange = vm::onPasswordChange,
            label = "Password",
            enabled = state.canSubmit,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Go,
            onImeAction = vm::submit,
            password = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        ErrorText(state.error, Modifier.fillMaxWidth())
        if (state.error != null) Spacer(Modifier.height(16.dp))
        TvButton(
            text = when {
                state.signedIn -> "Signed in"
                state.busy -> "Signing in…"
                else -> "Sign in"
            },
            onClick = vm::submit,
            enabled = state.canSubmit,
            style = TvButtonStyle.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        TvButton(
            text = "Use the QR code instead",
            onClick = onUsePairing,
            enabled = !state.signedIn,
            style = TvButtonStyle.Subtle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
