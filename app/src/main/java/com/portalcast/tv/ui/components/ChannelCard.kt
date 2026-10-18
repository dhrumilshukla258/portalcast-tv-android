package com.portalcast.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.ui.theme.AppColors

/**
 * A live channel tile: logo, number + name, the current programme and its progress.
 * Same focus treatment as [MediaCard] (scale + white ring).
 *
 * @param nowPlaying title of the programme on now (from EPG), or null to hide the line.
 * @param nowProgress 0..1 through the current programme, or null for no bar.
 * @param onLongClick long-press Center, touch long-press **and the Menu key** (favourite toggle).
 */
@Composable
fun ChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    nowPlaying: String? = null,
    nowProgress: Float? = null,
    isFavorite: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    val menuKey = if (onLongClick != null) {
        Modifier.onPreviewKeyEvent { e ->
            if (e.key == Key.Menu && e.type == KeyEventType.KeyUp) { onLongClick(); true } else false
        }
    } else Modifier
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().then(menuKey),
        shape = CardDefaults.shape(shape = shape),
        scale = CardDefaults.scale(focusedScale = 1.06f, pressedScale = 1.03f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, AppColors.Focus), shape = shape),
            pressedBorder = Border(BorderStroke(3.dp, AppColors.Focus), shape = shape),
        ),
        colors = CardDefaults.colors(
            containerColor = AppColors.Surface,
            contentColor = AppColors.Text,
            focusedContainerColor = AppColors.SurfaceVariant,
            focusedContentColor = AppColors.Text,
            pressedContainerColor = AppColors.SurfaceVariant,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtImage(
                url = rememberImageUrl(channel.artwork),
                fallbackText = channel.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    channel.number?.let {
                        Text(
                            "$it",
                            style = MaterialTheme.typography.labelLarge,
                            color = AppColors.Primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Text(
                        channel.displayName.ifBlank { "Channel ${channel.id}" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isFavorite) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = "Favourite",
                            tint = AppColors.Error,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (!nowPlaying.isNullOrBlank()) {
                    Text(
                        nowPlaying,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (nowProgress != null) {
                    Spacer(Modifier.height(6.dp))
                    ProgressLine(nowProgress, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
