package com.portalcast.tv.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.appContainer
import com.portalcast.tv.ui.components.ErrorText
import com.portalcast.tv.ui.components.QrCodeImage
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.components.TvButtonStyle
import com.portalcast.tv.ui.theme.AppColors

/**
 * Primary TV sign-in (plan decision #2): QR + user code, polled every 4 s by
 * [PairingViewModel]. Side by side on a TV, stacked on a phone. Polling stops when
 * this leaves composition and resumes with the same code when it comes back.
 */
@Composable
fun PairingScreen(onUsePassword: () -> Unit, onChangeServer: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm: PairingViewModel = viewModel(
        factory = viewModelFactory { initializer { PairingViewModel(container.authRepository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val serverUrl by container.authRepository.serverUrl.collectAsStateWithLifecycle()

    DisposableEffect(vm) {
        vm.onScreenEntered()
        onDispose { vm.onScreenLeft() }
    }

    // Keep focus on the first action whenever the set of actions changes (code → expired → new code).
    val firstAction = remember { FocusRequester() }
    LaunchedEffect(state::class) { runCatching { firstAction.requestFocus() } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 880.dp
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (wide) 1100.dp else 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("PORTALCAST", style = MaterialTheme.typography.labelLarge, color = AppColors.Primary)
                Spacer(Modifier.height(8.dp))
                Text("Pair this TV", style = MaterialTheme.typography.headlineLarge, color = AppColors.Text)
                Spacer(Modifier.height(8.dp))
                Text(
                    "On your phone or computer, scan the code or open the link, sign in, and confirm the code shown here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))

                val s = state
                if (wide) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(320.dp), contentAlignment = Alignment.Center) { QrPanel(s) }
                        Spacer(Modifier.width(56.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            CodePanel(s)
                            Spacer(Modifier.height(32.dp))
                            Actions(s, vm::regenerate, onUsePassword, onChangeServer, firstAction)
                        }
                    }
                } else {
                    QrPanel(s)
                    Spacer(Modifier.height(24.dp))
                    CodePanel(s)
                    Spacer(Modifier.height(28.dp))
                    Actions(s, vm::regenerate, onUsePassword, onChangeServer, firstAction)
                }

                Spacer(Modifier.height(28.dp))
                serverUrl?.let {
                    Text("Server: $it", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun QrPanel(state: PairingState) {
    if (state is PairingState.Showing) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            QrCodeImage(content = state.code.verificationUrl, size = 260.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                state.code.verificationUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CodePanel(state: PairingState) {
    when (state) {
        PairingState.Loading -> Text("Generating code…", style = MaterialTheme.typography.titleLarge, color = AppColors.TextMuted)
        is PairingState.Showing -> {
            Text("Your code", style = MaterialTheme.typography.labelLarge, color = AppColors.TextMuted)
            Spacer(Modifier.height(8.dp))
            Text(
                state.code.userCode,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 72.sp,
                letterSpacing = 8.sp,
                color = AppColors.Primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text("Waiting for approval…", style = MaterialTheme.typography.bodyLarge, color = AppColors.TextMuted)
        }
        PairingState.Expired -> Text(
            "This code has expired.",
            style = MaterialTheme.typography.titleLarge,
            color = AppColors.Error,
            textAlign = TextAlign.Center,
        )
        is PairingState.Failed -> ErrorText(state.message)
        PairingState.Authorized -> Text("Approved. Signing in…", style = MaterialTheme.typography.titleLarge, color = AppColors.Text)
    }
}

@Composable
private fun Actions(
    state: PairingState,
    onRegenerate: () -> Unit,
    onUsePassword: () -> Unit,
    onChangeServer: () -> Unit,
    firstAction: FocusRequester,
) {
    val needsNewCode = state is PairingState.Expired || state is PairingState.Failed
    Column(
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (needsNewCode) {
            TvButton(
                text = "Generate new code",
                onClick = onRegenerate,
                style = TvButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth().focusRequester(firstAction),
            )
        }
        TvButton(
            text = "Use email and password instead",
            onClick = onUsePassword,
            enabled = state !is PairingState.Authorized,
            modifier = Modifier.fillMaxWidth().then(if (needsNewCode) Modifier else Modifier.focusRequester(firstAction)),
        )
        TvButton(
            text = "Change server",
            onClick = onChangeServer,
            enabled = state !is PairingState.Authorized,
            style = TvButtonStyle.Subtle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
