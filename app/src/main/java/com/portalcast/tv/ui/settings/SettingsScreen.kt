package com.portalcast.tv.ui.settings

import com.portalcast.tv.ui.components.TvTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
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
        factory = viewModelFactory { initializer { SettingsViewModel(container.authRepository, container.localPrefs, container.api) } },
    )
    val serverUrl by vm.serverUrl.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val directLive by vm.directLiveStreams.collectAsStateWithLifecycle()
    val externalPlayer by vm.externalPlayer.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val players = remember { com.portalcast.tv.playback.ExternalPlayer.installedPlayers(context) }
    var choosingPlayer by remember { androidx.compose.runtime.mutableStateOf(false) }
    val subs by vm.subtitleAccounts.collectAsStateWithLifecycle()
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
                        if (directLive) "Live TV plays straight from the provider's CDN, if the server allows it (ALLOW_DIRECT_STREAMS); otherwise it is relayed."
                        else "Live TV is relayed through the PortalCast server (default; works everywhere, provider address stays hidden).",
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
            Spacer(Modifier.height(8.dp))

            // Settings › Player: the built-in player, Android's chooser, or an installed app.
            val playerOptions = listOf(
                com.portalcast.tv.playback.ExternalPlayer.BUILT_IN to "Built-in player",
                com.portalcast.tv.playback.ExternalPlayer.ASK to "Ask every time",
            ) + players.map { it.packageName to it.label }
            val playerLabel = playerOptions.firstOrNull { it.first == externalPlayer }?.second ?: externalPlayer
            ListItem(
                selected = false,
                onClick = { choosingPlayer = !choosingPlayer },
                modifier = Modifier.fillMaxWidth(),
                headlineContent = { Text("Player") },
                supportingContent = {
                    Text(
                        if (externalPlayer == com.portalcast.tv.playback.ExternalPlayer.BUILT_IN) "Built-in player"
                        else "$playerLabel. It gets this server's stream link only; where you stop is saved when you come back.",
                    )
                },
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
            if (choosingPlayer) {
                for ((value, label) in playerOptions) {
                    ListItem(
                        selected = value == externalPlayer,
                        onClick = { vm.setExternalPlayer(value); choosingPlayer = false },
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp),
                        headlineContent = { Text(label) },
                        trailingContent = { if (value == externalPlayer) Text("✓", color = AppColors.Focus) },
                        shape = ListItemDefaults.shape(shape = RowShape),
                        colors = ListItemDefaults.colors(
                            containerColor = AppColors.Surface,
                            contentColor = AppColors.Text,
                            focusedContainerColor = AppColors.SurfaceVariant,
                            focusedContentColor = AppColors.Text,
                            selectedContainerColor = AppColors.Surface,
                            selectedContentColor = AppColors.Text,
                            pressedContainerColor = AppColors.SurfaceVariant,
                            pressedContentColor = AppColors.Text,
                        ),
                        border = ListItemDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, AppColors.Focus), shape = RowShape),
                        ),
                    )
                }
                if (players.isEmpty()) {
                    Text(
                        "No other video player is installed. VLC, MX Player or Just Player from the store will show up here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextMuted,
                        modifier = Modifier.padding(start = 24.dp, top = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))

            SubtitleAccountsSection(subs, vm)
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
                "PortalCast ${BuildConfig.VERSION_NAME}",
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

/**
 * The user's own subtitle accounts. Downloads then use their own quota, and work even when
 * the server has no key. Keys/passwords are sent once and stored encrypted by the server;
 * they are never shown again.
 */
@Composable
private fun SubtitleAccountsSection(s: SubtitleAccountsUi, vm: SettingsViewModel) {
    SectionLabel("Subtitle accounts")
    if (s.unsupported) {
        InfoRow(title = "Not available", subtitle = "Update the PortalCast server to add your own subtitle accounts.")
        return
    }
    val st = s.status
    val osLine = when {
        st == null -> "Loading…"
        st.linked -> "OpenSubtitles: signed in as ${st.username}"
        else -> "OpenSubtitles: not signed in"
    }
    val osKey = when {
        st == null -> null
        st.hasOwnApiKey -> "Using your own API key"
        st.serverHasApiKey -> "Using the server's API key"
        else -> "No API key yet: add one (free at opensubtitles.com/consumers)"
    }
    InfoRow(title = osLine, subtitle = osKey)
    Spacer(Modifier.height(8.dp))
    TvTextField(value = s.osUsername, onValueChange = vm::setOsUsername, label = "OpenSubtitles username", modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    TvTextField(value = s.osPassword, onValueChange = vm::setOsPassword, label = "Password", password = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    TvTextField(value = s.osApiKey, onValueChange = vm::setOsApiKey, label = "API key (optional)", hint = "Only if the server has none", modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TvButton(text = if (s.busy) "Saving…" else "Save OpenSubtitles", onClick = vm::saveOpenSubtitles, enabled = !s.busy, style = TvButtonStyle.Primary)
        if (st?.linked == true || st?.hasOwnApiKey == true) TvButton(text = "Remove", onClick = vm::removeOpenSubtitles, enabled = !s.busy)
    }

    Spacer(Modifier.height(16.dp))
    val subdl = st?.subdl
    InfoRow(
        title = when {
            subdl == null -> "SubDL"
            subdl.hasOwnApiKey -> "SubDL: using your own API key"
            subdl.serverHasApiKey -> "SubDL: using the server's API key"
            else -> "SubDL: no API key"
        },
        subtitle = "Second subtitle source. Free key at subdl.com.",
    )
    Spacer(Modifier.height(8.dp))
    TvTextField(value = s.subdlKey, onValueChange = vm::setSubdlKey, label = "SubDL API key", modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TvButton(text = if (s.busy) "Saving…" else "Save SubDL key", onClick = vm::saveSubdl, enabled = !s.busy, style = TvButtonStyle.Primary)
        if (subdl?.hasOwnApiKey == true) TvButton(text = "Remove", onClick = vm::removeSubdl, enabled = !s.busy)
    }
    s.message?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
    }
}
