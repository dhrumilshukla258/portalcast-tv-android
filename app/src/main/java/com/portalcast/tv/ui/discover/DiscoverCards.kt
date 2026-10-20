@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)

package com.portalcast.tv.ui.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.portalcast.tv.data.model.DiscoverFacets
import com.portalcast.tv.data.model.DiscoverQuery
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.components.TvButtonStyle
import com.portalcast.tv.ui.theme.AppColors
import com.portalcast.tv.util.ImageUrl
import kotlinx.coroutines.delay

/*
 * Discover-private cards and dialogs. Shared cards for the other tabs live in
 * ui/components (another phase owns those); these stay here on purpose.
 */

internal val PosterWidth: Dp = 130.dp

internal fun Modifier.posterAspect(): Modifier = aspectRatio(2f / 3f)


private val CardShape = RoundedCornerShape(14.dp)

/**
 * 2:3 glass poster tile (webui style: white-8 % fill, 1 dp white-10 % border at rest,
 * 3 dp white ring + 1.08 scale on focus) with the title underneath. Deliberately light
 * for a modest TV: one poster decoded at its displayed size, no crossfade, no shadow.
 * [progressPercent] draws a sky-400 bar along the poster's bottom.
 */
@Composable
internal fun PosterCard(
    title: String,
    subtitle: String?,
    imagePath: String?,
    serverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progressPercent: Int? = null,
    onFocused: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    channel: String? = null,
    channelLogo: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val request = remember(serverUrl, imagePath) {
        ImageUrl.resolve(serverUrl, imagePath)?.let { url ->
            val w = with(density) { PosterWidth.roundToPx() }
            ImageRequest.Builder(context).data(url).size(w, w * 3 / 2).build()
        }
    }
    Column(modifier.width(PosterWidth)) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .posterAspect()
                .onFocusChanged {
                    if (focused != it.isFocused) focused = it.isFocused
                    if (it.isFocused) onFocused()
                },
            shape = CardDefaults.shape(shape = CardShape),
            scale = CardDefaults.scale(focusedScale = 1.08f),
            border = CardDefaults.border(
                border = Border(BorderStroke(1.dp, AppColors.GlassBorder), shape = CardShape),
                focusedBorder = Border(BorderStroke(3.dp, AppColors.Focus), shape = CardShape),
                pressedBorder = Border(BorderStroke(3.dp, AppColors.Focus), shape = CardShape),
            ),
            colors = CardDefaults.colors(
                containerColor = AppColors.GlassFill,
                focusedContainerColor = AppColors.GlassFillFocused,
                pressedContainerColor = AppColors.GlassFillFocused,
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (request != null) {
                    AsyncImage(
                        model = request,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        title,
                        modifier = Modifier.align(Alignment.Center).padding(8.dp),
                        color = AppColors.TextMuted,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (channel != null) {
                    ChannelBadge(channel, channelLogo, serverUrl, Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                if (progressPercent != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(AppColors.Background.copy(alpha = 0.7f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressPercent.coerceIn(0, 100) / 100f)
                                .background(AppColors.Primary),
                        )
                    }
                }
            }
        }
        Text(
            title,
            modifier = Modifier.padding(top = 8.dp),
            color = if (focused) AppColors.Text else AppColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                color = AppColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Dialog panel: near-opaque dark glass with a white-10 % hairline, over the [AppColors.GlassScrim]. */
private fun Modifier.glassPanel(): Modifier = this
    .background(AppColors.Background.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
    .border(1.dp, AppColors.GlassBorder, RoundedCornerShape(16.dp))

@Composable
private fun DialogFrame(content: @Composable () -> Unit) {
    Box(
        Modifier
            .width(720.dp)
            .glassPanel()
            .padding(28.dp),
    ) { content() }
}

/**
 * Shown when a Discover group has more than one variant (language/quality versions);
 * the webui's VariantPickerModal for a TV: a centered glass panel over the dimmed
 * screen with the title's poster, the title, and one large row per version (the
 * label's language part prominent, subtitle/quality tags as chips, from
 * [VariantLabels]). The first row takes focus; Back (or Cancel) closes.
 */
@Composable
internal fun VariantPickerDialog(
    variants: List<MediaItem>,
    serverUrl: String?,
    onChoose: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val lead = variants.firstOrNull()
    val title = remember(variants) { variants.firstOrNull { it.displayTitle.isNotBlank() }?.displayTitle ?: "Choose a version" }
    val context = LocalContext.current
    val density = LocalDensity.current
    val poster = remember(variants, serverUrl) {
        val path = variants.firstNotNullOfOrNull { it.screenshotUri?.takeIf { p -> p.isNotBlank() } }
        ImageUrl.resolve(serverUrl, path)?.let { url ->
            val w = with(density) { 170.dp.roundToPx() }
            ImageRequest.Builder(context).data(url).size(w, w * 3 / 2).build()
        }
    }
    val meta = remember(lead) {
        lead?.let { listOfNotNull(it.displayYear, it.displayRating, it.displayGenres).joinToString("  ·  ") }.orEmpty()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(AppColors.GlassScrim), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .width(820.dp)
                    .glassPanel()
                    .padding(28.dp),
            ) {
                Box(
                    Modifier
                        .width(170.dp)
                        .posterAspect()
                        .clip(CardShape)
                        .background(AppColors.GlassFill)
                        .border(1.dp, AppColors.GlassBorder, CardShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title.take(1).uppercase(), style = MaterialTheme.typography.headlineLarge, color = AppColors.TextMuted)
                    if (poster != null) {
                        AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted, modifier = Modifier.padding(top = 4.dp))
                    }
                    Text(
                        "Available in ${variants.size} versions. Pick one:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextMuted,
                        modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
                    )
                    // Padding inside the scroll so the focused row's scale and ring are not clipped.
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .heightIn(max = 330.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                    ) {
                        variants.forEachIndexed { i, v ->
                            VariantRow(
                                index = i,
                                variant = v,
                                onClick = { onChoose(v) },
                                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                        TvButton("Cancel", onClick = onDismiss, style = TvButtonStyle.Subtle)
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            // The dialog window takes a frame or two to attach; retry until focus lands.
            repeat(5) {
                withFrameNanos { }
                if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(50)
            }
        }
    }
}

@Composable
private fun VariantRow(index: Int, variant: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val parts = remember(variant.variantLabel, variant.displayTitle) {
        VariantLabels.parse(variant.variantLabel?.takeIf { it.isNotBlank() } ?: variant.displayTitle)
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = CardShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, AppColors.GlassBorder), shape = CardShape),
            focusedBorder = Border(BorderStroke(3.dp, AppColors.Focus), shape = CardShape),
            pressedBorder = Border(BorderStroke(3.dp, AppColors.Focus), shape = CardShape),
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
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).background(AppColors.GlassFillFocused, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = AppColors.Text)
            }
            Spacer(Modifier.width(16.dp))
            Text(
                parts.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (parts.chips.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    parts.chips.forEach { VariantChipView(it) }
                }
            }
        }
    }
}

@Composable
private fun VariantChipView(chip: VariantChip) {
    val color = when (chip.kind) {
        VariantChipKind.Quality -> AppColors.Primary
        VariantChipKind.Subtitles -> AppColors.Secondary
    }
    Text(
        chip.text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}


