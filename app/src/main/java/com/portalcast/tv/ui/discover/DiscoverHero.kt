@file:OptIn(ExperimentalFoundationApi::class)

package com.portalcast.tv.ui.discover

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.ui.components.Formatting
import com.portalcast.tv.ui.theme.AppColors
import com.portalcast.tv.util.ImageUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import com.portalcast.tv.util.HERO_BACKDROP

/** How long focus must rest on a card before the hero backdrop follows it. */
private const val HERO_IMAGE_DEBOUNCE_MS = 400L

/** Decode size for the hero backdrop (px): about its on-screen size on a 1080p panel. */
private const val HERO_IMAGE_W = 1280
private const val HERO_IMAGE_H = 720

/**
 * The focused Discover item, hoisted out of the rows. Cards only WRITE [item]; only the
 * hero composables READ it, so a d-pad step recomposes the hero and nothing else.
 * [fallback] (first item of the first row) is shown until something is focused.
 */
@Stable
internal class HeroFocus {
    var item by mutableStateOf<MediaItem?>(null)
    var fallback by mutableStateOf<MediaItem?>(null)
    val shown: MediaItem? get() = item ?: fallback
}

/** Hero art: the TMDB backdrop (regular before HD: cheaper to decode), else the poster. */
internal val MediaItem.heroArtPath: String?
    get() = backdropPath?.takeIf { it.isNotBlank() } ?: backdropHdPath?.takeIf { it.isNotBlank() }
        ?: screenshotUri?.takeIf { it.isNotBlank() }

/**
 * The single large image on Discover: the shown item's backdrop, right-aligned in the
 * top of the screen and faded into the background with a horizontal and a vertical
 * gradient (the webui's AmbientBackdrop, without blur: API 30 has no RenderEffect).
 * It follows focus only after focus rests [HERO_IMAGE_DEBOUNCE_MS], and fades once
 * from the previous image (kept as the placeholder) to the new one.
 *
 * The whole thing (bitmap + two full-screen gradients) is composited offscreen: the GPU
 * then rasterises it once per image change and blits the cached layer on every scroll
 * frame, instead of re-shading ~2M gradient pixels per frame (Mali-G52 at 1080p spent
 * up to 80 ms per frame on "issue draw commands" doing that).
 */
@Composable
internal fun HeroBackdrop(focus: HeroFocus, serverUrl: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var url by remember { mutableStateOf<String?>(null) }
    var previous by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focus, serverUrl) {
        snapshotFlow { focus.shown?.heroArtPath }
            .distinctUntilChanged()
            .collectLatest { path ->
                // No art: keep the last image rather than flashing to nothing.
                val next = ImageUrl.resolve(serverUrl, path) ?: return@collectLatest
                if (url != null) delay(HERO_IMAGE_DEBOUNCE_MS)
                previous = url
                url = next
            }
    }
    val request = remember(url) {
        url?.let {
            ImageRequest.Builder(context)
                .data(ImageUrl.downsize(it, HERO_BACKDROP))
                .size(HERO_IMAGE_W, HERO_IMAGE_H)
                .apply { previous?.let { p -> placeholderMemoryCacheKey(MemoryCache.Key(p)) } }
                .crossfade(250)
                .build()
        }
    }
    Box(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                alpha = 0.85f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to AppColors.Background,
                    0.3f to AppColors.Background.copy(alpha = 0.7f),
                    0.65f to AppColors.Background.copy(alpha = 0.1f),
                    1f to AppColors.Background.copy(alpha = 0f),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to AppColors.Background.copy(alpha = 0.35f),
                    0.25f to AppColors.Background.copy(alpha = 0f),
                    0.6f to AppColors.Background.copy(alpha = 0.3f),
                    1f to AppColors.Background,
                ),
            ),
        )
    }
}

/** Title, meta line (year · rating · genres · runtime) and a 3-line overview of the shown item. */
@Composable
internal fun HeroInfo(focus: HeroFocus, modifier: Modifier = Modifier, serverUrl: String? = null) {
    val item = focus.shown
    Box(modifier.padding(start = 48.dp, end = 48.dp, bottom = 8.dp), contentAlignment = Alignment.BottomStart) {
        if (item != null) {
            Column(Modifier.widthIn(max = 620.dp)) {
                Text(
                    item.displayTitle.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = remember(item) { Formatting.metaLine(item) }
                val channel = item.network
                if (meta.isNotBlank() || channel != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (channel != null) {
                            ChannelBadge(channel, item.networkLogo, serverUrl, height = 26.dp)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            meta,
                            style = MaterialTheme.typography.titleSmall,
                            color = AppColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    item.overview.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Text.copy(alpha = 0.85f),
                    maxLines = 3,
                    minLines = 3, // fixed height, so nothing below moves as the overview length changes
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Put on each Discover row (a LazyColumn item). Whatever inside the row asks to be
 * brought into view (the focused card), the column is handed the row's top edge with a
 * viewport-tall rect, which the default bring-into-view maths resolves to "scroll so
 * this row's top is the column's top". So the focused row always sits directly under
 * the hero (Netflix), and moving left/right inside it never moves the column.
 * Done here rather than with animateScrollToItem on focus, because that and the
 * default bring-into-view would be two scrolls fighting over the list on every Up/Down.
 */
internal class PivotRowToTop(private val viewportHeight: () -> Int) : BringIntoViewResponder {
    override fun calculateRectForParent(localRect: Rect): Rect {
        val h = viewportHeight()
        return if (h > 0) Rect(localRect.left, 0f, localRect.right, h.toFloat()) else localRect
    }

    override suspend fun bringChildIntoView(localRect: () -> Rect?) = Unit
}

/**
 * Put on each card of a LazyRow: widens the focused card's bring-into-view rect by
 * [marginPx] on both sides, so the row scrolls far enough that the card (with its focus
 * scale and ring) is fully visible with room to spare, not flush against the edge.
 */
internal class PadRectHorizontally(private val marginPx: Float) : BringIntoViewResponder {
    override fun calculateRectForParent(localRect: Rect): Rect =
        Rect(localRect.left - marginPx, localRect.top, localRect.right + marginPx, localRect.bottom)

    override suspend fun bringChildIntoView(localRect: () -> Rect?) = Unit
}
