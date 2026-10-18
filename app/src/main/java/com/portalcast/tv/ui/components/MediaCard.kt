package com.portalcast.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.ui.theme.AppColors

/** Poster aspect ratio (2:3). */
const val PosterAspect = 2f / 3f

/** 16:9, for episode stills and wide cards. */
const val WideAspect = 16f / 9f

/**
 * A focusable poster tile: tv-material `Card` with a strong focus treatment (1.1x scale,
 * 3 dp white ring), the image cropped to [aspectRatio], an optional watched-progress bar
 * along the bottom, and the title (+ optional subtitle) under the card. The title
 * brightens while the card has focus.
 *
 * @param imageUrl an absolute URL (resolve with [rememberImageUrl]); null shows the title's initial.
 * @param progress 0..1 watched fraction, or null/0 for no bar.
 * @param width fixed width; [Dp.Unspecified] fills the parent (grid cells).
 * @param onLongClick long-press Center (and touch long-press).
 *
 * Attach `focusRequester`/`onFocusChanged` through [modifier]; they reach the card.
 */
@Composable
fun MediaCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    width: Dp = 150.dp,
    aspectRatio: Float = PosterAspect,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier.then(if (width == Dp.Unspecified) Modifier.fillMaxWidth() else Modifier.width(width))) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
            shape = CardDefaults.shape(shape = shape),
            scale = CardDefaults.scale(focusedScale = 1.1f, pressedScale = 1.05f),
            border = CardDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, AppColors.Focus), shape = shape),
                pressedBorder = Border(BorderStroke(3.dp, AppColors.Focus), shape = shape),
            ),
            colors = CardDefaults.colors(
                containerColor = AppColors.Surface,
                focusedContainerColor = AppColors.Surface,
                pressedContainerColor = AppColors.Surface,
            ),
            interactionSource = interaction,
        ) {
            Box(Modifier.fillMaxSize()) {
                ArtImage(url = imageUrl, fallbackText = title, modifier = Modifier.fillMaxSize())
                if (progress != null && progress > 0f) {
                    ProgressLine(
                        fraction = progress,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Text(
            text = title.ifBlank { " " },
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) AppColors.Text else AppColors.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** [MediaCard] for a [MediaItem]: poster from `screenshot_uri`, title from `displayTitle`. */
@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = item.displayYear,
    progress: Float? = null,
    width: Dp = 150.dp,
    onLongClick: (() -> Unit)? = null,
) {
    MediaCard(
        title = item.displayTitle,
        imageUrl = rememberImageUrl(item.posterPath),
        onClick = onClick,
        modifier = modifier,
        subtitle = subtitle,
        progress = progress,
        width = width,
        onLongClick = onLongClick,
    )
}

/**
 * An image filling its bounds (cropped), or a dim tile with [fallbackText]'s first
 * letter when [url] is null or fails to load.
 */
@Composable
fun ArtImage(url: String?, fallbackText: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    Box(modifier.background(AppColors.SurfaceVariant), contentAlignment = Alignment.Center) {
        Text(
            text = fallbackText.trim().firstOrNull()?.uppercase() ?: "",
            style = MaterialTheme.typography.headlineMedium,
            color = AppColors.TextMuted,
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A 4 dp rounded progress bar (sky-400 on a translucent track). [fraction] is clamped to 0..1. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color = AppColors.Primary) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.25f)),
    ) {
        if (f > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(color))
    }
}
