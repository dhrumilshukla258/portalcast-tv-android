package com.portalcast.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.appContainer
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

/** `GET /api/v2/health/iptv` (servers from 2026-10-21). */
@Serializable
data class IptvHealth(val iptv: String = "online", val since: String? = null)

/**
 * Whether the server can reach the IPTV portal. While it cannot (internet down), IPTV cards
 * are dimmed and a banner says so; local-library titles stay bright and keep playing.
 */
object IptvStatus {
    val offline = mutableStateOf(false)
}

/** Asks the server once a minute while the home screen is up. Older servers: always online. */
@Composable
fun IptvStatusPoller() {
    val api = LocalContext.current.appContainer.api
    LaunchedEffect(Unit) {
        while (true) {
            val h = runCatching { api.iptvHealth() }.getOrNull()
            IptvStatus.offline.value = h?.iptv == "offline"
            delay(60_000)
        }
    }
}

@Composable
fun IptvOfflineBanner(modifier: Modifier = Modifier) {
    if (!IptvStatus.offline.value) return
    Text(
        "IPTV is offline · your library still plays",
        modifier = modifier
            .background(Color(0xE6B45309), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
    )
}

/** Dims an IPTV card while IPTV is offline; a local title ([isLocal]) is never dimmed. */
@Composable
fun Modifier.dimWhenIptvOffline(isLocal: Boolean): Modifier =
    if (IptvStatus.offline.value && !isLocal) this.alpha(0.35f) else this
