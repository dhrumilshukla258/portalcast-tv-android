package com.portalcast.tv.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.Border
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.portalcast.tv.BuildConfig
import com.portalcast.tv.appContainer
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.components.TvButtonStyle
import com.portalcast.tv.ui.theme.AppColors

/**
 * Server (read-only), signed-in user, the direct-live-streams toggle and Sign out.
 * Sign out clears the session; the nav gate then returns to pairing on its own.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(container.authRepository, container.localPrefs) } },
    )
    val serverUrl by vm.serverUrl.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val directLive by vm.directLiveStreams.collectAsStateWithLifecycle()
    val firstItem = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { firstItem.requestFocus() } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 40.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineLarge, color = AppColors.Text)
            Spacer(Modifier.height(24.dp))

            SectionLabel("Server")
            InfoRow(title = serverUrl ?: "Not set")
            Spacer(Modifier.height(20.dp))

            SectionLabel("Signed in as")
            InfoRow(
                title = user?.displayName ?: "Not signed in",
                subtitle = user?.let { u -> listOfNotNull(u.email.takeIf { it.isNotBlank() }, u.role).joinToString(" · ") },
            )
            Spacer(Modifier.height(20.dp))

            SectionLabel("Playback")
            ListItem(
                selected = false,
                onClick = vm::toggleDirectLiveStreams,
                modifier = Modifier.fillMaxWidth().focusRequester(firstItem),
                headlineContent = { Text("Direct live streams") },
                supportingContent = {
                    Text(
                        if (directLive) "Live TV plays straight from the provider's CDN (server sends a redirect)."
                        else "Live TV is relayed through the Portalcast server (default; works everywhere).",
                    )
                },
                trailingContent = { Switch(checked = directLive, onCheckedChange = null) },
                shape = ListItemDefaults.shape(shape = RowShape),
                colors = ListItemDefaults.colors(
                    containerColor = AppColors.Surface,
                    contentColor = AppColors.Text,
                    focusedContainerColor = AppColors.SurfaceVariant,
                    focusedContentColor = AppColors.Text,
                    pressedContainerColor = AppColors.SurfaceVariant,
                    pressedContentColor = AppColors.Text,
                ),
                border = ListItemDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, AppColors.Focus), shape = RowShape),
                ),
            )
            Spacer(Modifier.height(28.dp))

            TvButton(
                text = "Sign out",
                onClick = vm::signOut,
                style = TvButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            TvButton(
                text = "Back",
                onClick = onBack,
                style = TvButtonStyle.Subtle,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "PortalcastTV ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextMuted,
            )
        }
    }
}

private val RowShape = RoundedCornerShape(10.dp)

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = AppColors.TextMuted, modifier = Modifier.padding(bottom = 8.dp))
}

/** Non-focusable read-only row styled like the interactive ones. */
@Composable
private fun InfoRow(title: String, subtitle: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Surface, RowShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.Text)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
        }
    }
}
