package com.portalcast.tv.ui.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.EpgProgramme
import com.portalcast.tv.ui.components.ProgressLine
import com.portalcast.tv.ui.theme.AppColors
import com.portalcast.tv.util.ImageUrl

/**
 * The EPG index behind a stable, identity-compared handle, so list rows skip recomposition
 * until the EPG itself is replaced (a data class would compare the whole map per row).
 */
private val ROW_HEIGHT = 72.dp

@Immutable
class EpgLookup(private val byChannel: Map<String, List<EpgProgramme>>) {
    operator fun get(channelId: String): List<EpgProgramme> = byChannel[channelId].orEmpty()

    companion object {
        val Empty = EpgLookup(emptyMap())
    }
}

/**
 * One channel in the Live list: number, small logo, name on one line, now-playing with a
 * thin progress bar and the next programme in muted text. Center plays, long-press Center
 * or Menu toggles the favourite. Everything shown is derived here from cheap, remembered
 * values; the row reads no screen-wide state, so moving focus does not recompose the list.
 */
@Composable
fun ChannelRow(
    channel: Channel,
    epg: EpgLookup,
    nowMs: Long,
    use24h: Boolean,
    serverUrl: String?,
    isFavorite: Boolean,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onFocused: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val programmes = epg[channel.id]
    val (current, next) = remember(programmes, nowMs) { LiveEpg.nowNext(programmes, nowMs) }
    val currentTitle = remember(current) { current?.let(LiveEpg::title) }
    val nextLine = remember(next, use24h) { next?.let { LiveEpg.nextLine(it, use24h) } }
    val logoUrl = remember(serverUrl, channel.artwork) { ImageUrl.resolve(serverUrl, channel.artwork) }
    val name = remember(channel.id, channel.displayName) { channel.displayName.ifBlank { "Channel ${channel.id}" } }

    // Glass, like the webui's channel list panel (no blur: Modifier.blur needs API 31).
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = { onPlay(channel) },
        onLongClick = { onToggleFavorite(channel) },
        // Key handlers and focus observers go before the Surface's own focusable (CLAUDE.md).
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { e ->
                if (e.key == Key.Menu && e.type == KeyEventType.KeyUp) { onToggleFavorite(channel); true } else false
            }
            .onFocusChanged { if (it.hasFocus) onFocused(channel) },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.99f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, AppColors.GlassBorder), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, AppColors.Focus), shape = shape),
            pressedBorder = Border(BorderStroke(3.dp, AppColors.Focus), shape = shape),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.GlassFill,
            contentColor = AppColors.Text,
            focusedContainerColor = AppColors.GlassFillFocused,
            focusedContentColor = AppColors.Text,
            pressedContainerColor = AppColors.GlassFillFocused,
            pressedContentColor = AppColors.Text,
        ),
    ) {
        // Fixed height: rows with and without EPG line up, and the lazy list measures cheaply.
        Row(Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                channel.number?.toString() ?: "",
                style = MaterialTheme.typography.labelLarge,
                color = AppColors.TextMuted,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(44.dp),
            )
            Spacer(Modifier.width(10.dp))
            ChannelLogo(logoUrl, name, width = 60.dp, height = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isFavorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.Favorite, contentDescription = "Favourite", tint = AppColors.Error, modifier = Modifier.size(14.dp))
                    }
                }
                if (current != null) {
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            currentTitle.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.Text.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        ProgressLine(current.progressAt(nowMs), Modifier.width(56.dp))
                    }
                }
                if (nextLine != null) {
                    Text(
                        nextLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

/**
 * A channel logo at a fixed size, decoded at that size (Coil would otherwise decode the
 * source at full resolution). Falls back to the name's initial when there is no logo or
 * it fails to load.
 */
@Composable
fun ChannelLogo(url: String?, name: String, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        modifier.size(width, height).clip(RoundedCornerShape(8.dp)).background(AppColors.GlassFill),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null || failed) {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextMuted,
            )
        } else {
            val context = LocalContext.current
            val density = LocalDensity.current
            val request = remember(url, width, height) {
                with(density) {
                    ImageRequest.Builder(context).data(url).size(width.roundToPx(), height.roundToPx()).build()
                }
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize().padding(2.dp),
            )
        }
    }
}
