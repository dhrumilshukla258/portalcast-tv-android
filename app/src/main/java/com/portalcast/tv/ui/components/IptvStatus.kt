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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
 *
 * Every server answer carries the status (`X-IPTV` header, read by an interceptor in
 * AppContainer), so while the app is in use no request of its own is needed.
 */
object IptvStatus {
    val offline = mutableStateOf(false)

    @Volatile
    var lastHeardAt = 0L
        private set

    /** From an answer's `X-IPTV` header ("online" / "offline"). */
    fun report(value: String) {
        lastHeardAt = System.currentTimeMillis()
        val next = value.equals("offline", ignoreCase = true)
        if (offline.value != next) offline.value = next
    }
}

private const val QUIET_MS = 5 * 60_000L

/**
 * Asks the server only after 5 minutes without any answer (was every minute) while the
 * home screen is up, and only while the app is on screen: in the background it asks nothing,
 * and coming back checks at once. Older servers (no header, no endpoint): always online.
 */
@Composable
fun IptvStatusPoller() {
    val api = LocalContext.current.appContainer.api
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (System.currentTimeMillis() - IptvStatus.lastHeardAt >= QUIET_MS) {
                    val h = runCatching { api.iptvHealth() }.getOrNull()
                    IptvStatus.report(h?.iptv ?: "online")
                }
                delay(60_000)
            }
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
