package com.portalcast.tv.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.ui.components.ProgressLine
import com.portalcast.tv.ui.theme.AppColors
import com.portalcast.tv.util.ImageUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** How long focus must rest on a row before the details panel follows it. */
private const val SETTLE_MS = 150L

/**
 * Right-hand, non-focusable panel for the focused channel: logo, name, number, group,
 * the programme on now (time range, progress, description) and the next few. Only this
 * composable reads [focused], and only after focus has rested [SETTLE_MS], so scrolling
 * through the list does not rebuild it on every step.
 */
@Composable
fun ChannelDetails(
    focused: State<Channel?>,
    epg: EpgLookup,
    groupTitles: Map<String, String>,
    favorites: Set<String>,
    nowMs: Long,
    use24h: Boolean,
    serverUrl: String?,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf<Channel?>(null) }
    LaunchedEffect(focused) {
        snapshotFlow { focused.value }.collectLatest { c ->
            if (shown != null) delay(SETTLE_MS)
            shown = c
        }
    }

    Column(
        modifier
            .fillMaxSize()
            // Glass panel (webui glass-panel): translucent fill + hairline border, no blur.
            .background(AppColors.GlassFill, RoundedCornerShape(16.dp))
            .border(1.dp, AppColors.GlassBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        val channel = shown
        if (channel == null) {
            Text("Pick a channel to see what's on.", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
        } else {
            DetailsBody(channel, epg, groupTitles, favorites, nowMs, use24h, serverUrl)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.DetailsBody(
    channel: Channel,
    epg: EpgLookup,
    groupTitles: Map<String, String>,
    favorites: Set<String>,
    nowMs: Long,
    use24h: Boolean,
    serverUrl: String?,
) {
        val programmes = epg[channel.id]
        val (current, _) = remember(programmes, nowMs) { LiveEpg.nowNext(programmes, nowMs) }
        val upcoming = remember(programmes, nowMs) { LiveEpg.upcoming(programmes, nowMs, count = 3) }
        val logoUrl = remember(serverUrl, channel.artwork) { ImageUrl.resolve(serverUrl, channel.artwork) }
        val name = channel.displayName.ifBlank { "Channel ${channel.id}" }

        ChannelLogo(logoUrl, name, width = 150.dp, height = 90.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(
            channel.number?.let { "Ch $it" },
            channel.groupId?.let { groupTitles[it] }?.takeIf { it.isNotBlank() },
        ).joinToString("  ·  ")
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (channel.id in favorites) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Favorite, contentDescription = "Favourite", tint = AppColors.Error, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(18.dp))
        if (current != null) {
            Text("NOW", style = MaterialTheme.typography.labelMedium, color = AppColors.Primary)
            Text(
                LiveEpg.title(current),
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(LiveEpg.timeRange(current, use24h), style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
            Spacer(Modifier.height(6.dp))
            ProgressLine(current.progressAt(nowMs), Modifier.fillMaxWidth())
            val description = current.description?.trim().orEmpty()
            if (description.isNotEmpty()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else if (programmes.isEmpty()) {
            Text("No guide information for this channel.", style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
        }

        if (upcoming.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("NEXT", style = MaterialTheme.typography.labelMedium, color = AppColors.Primary)
            for (p in upcoming) {
                Row(Modifier.padding(top = 6.dp)) {
                    Text(
                        LiveEpg.clock(p.startMs, use24h),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextMuted,
                        modifier = Modifier.width(if (use24h) 48.dp else 64.dp),
                    )
                    Text(
                        LiveEpg.title(p),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text("Press OK to watch · Menu: favourite", style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
}
