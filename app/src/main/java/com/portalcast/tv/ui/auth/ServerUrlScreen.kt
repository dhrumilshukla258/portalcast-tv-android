package com.portalcast.tv.ui.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
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
 * First-run screen: one field for the server address, one Connect button.
 * Focus starts in the field; Go on the IME connects, d-pad Down reaches the button.
 * On success [onConnected] takes the user to pairing (the URL is now persisted).
 */
@Composable
fun ServerUrlScreen(onConnected: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm: ServerUrlViewModel = viewModel(
        factory = viewModelFactory { initializer { ServerUrlViewModel(container.authRepository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val fieldFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    LaunchedEffect(state.connected) {
        if (state.connected) {
            vm.consumeConnected()
            onConnected()
        }
    }

    AuthFrame(
        title = "Connect to your server",
        subtitle = "Enter the address of your Portalcast server. The port is needed unless it runs behind https.",
    ) {
        TvTextField(
            value = state.input,
            onValueChange = vm::onInputChange,
            label = "Server address",
            hint = "192.168.1.10:3000 or https://iptv.example.com",
            enabled = !state.busy,
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            onImeAction = vm::connect,
            focusRequester = fieldFocus,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        ErrorText(state.error, Modifier.fillMaxWidth())
        if (state.error != null) Spacer(Modifier.height(16.dp))
        TvButton(
            text = if (state.busy) "Connecting…" else "Connect",
            onClick = vm::connect,
            enabled = !state.busy,
            style = TvButtonStyle.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
