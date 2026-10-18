@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)

package com.portalcast.tv.ui.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.portalcast.tv.data.model.DiscoverFacets
import com.portalcast.tv.data.model.DiscoverQuery
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.components.TvButtonStyle
import com.portalcast.tv.ui.theme.AppColors

/*
 * Discover-private cards and dialogs. Shared cards for the other tabs live in
 * ui/components (another phase owns those); these stay here on purpose.
 */

internal val PosterWidth: Dp = 140.dp

internal fun Modifier.posterAspect(): Modifier = aspectRatio(2f / 3f)

private const val MAX_FACET_CHIPS = 40

/**
 * 2:3 poster with the title (and optional subtitle) underneath. Focus: scale 1.08 + a
 * 3 dp white ring. [progressPercent] draws a sky-400 bar along the poster's bottom.
 */
@Composable
internal fun PosterCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progressPercent: Int? = null,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.width(PosterWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .posterAspect()
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                },
            shape = CardDefaults.shape(shape = shape),
            scale = CardDefaults.scale(focusedScale = 1.08f),
            border = CardDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, AppColors.Focus), shape = shape),
            ),
            colors = CardDefaults.colors(containerColor = AppColors.Surface, focusedContainerColor = AppColors.Surface),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
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
        Text(
            subtitle.orEmpty(),
            color = AppColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DialogFrame(content: @Composable () -> Unit) {
    Box(
        Modifier
            .width(720.dp)
            .background(AppColors.Surface, RoundedCornerShape(16.dp))
            .padding(28.dp),
    ) { content() }
}

/** Shown when a Discover group has more than one variant (quality/language versions). */
@Composable
internal fun VariantPickerDialog(variants: List<MediaItem>, onChoose: (MediaItem) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        DialogFrame {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose a version", style = MaterialTheme.typography.headlineSmall, color = AppColors.Text)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                ) {
                    variants.forEachIndexed { i, v ->
                        val label = v.variantLabel?.takeIf { it.isNotBlank() } ?: v.displayTitle
                        TvButton(
                            text = label,
                            onClick = { onChoose(v) },
                            modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        )
                    }
                }
                TvButton("Cancel", onClick = onDismiss, style = TvButtonStyle.Subtle)
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

/**
 * Genre multi-select (AND on the server), single country and single language.
 * Edits a draft; Apply switches the screen into the filtered grid.
 */
@Composable
internal fun FiltersDialog(
    facets: DiscoverFacets,
    current: DiscoverQuery?,
    onApply: (DiscoverQuery) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var genres by remember { mutableStateOf(current?.genres.orEmpty().toSet()) }
    var country by remember { mutableStateOf(current?.country) }
    var language by remember { mutableStateOf(current?.language) }
    val applyFocus = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        DialogFrame {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Filters", style = MaterialTheme.typography.headlineSmall, color = AppColors.Text)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        "Apply",
                        onClick = { onApply(DiscoverQuery(genres = genres.toList(), country = country, language = language)) },
                        style = TvButtonStyle.Primary,
                        modifier = Modifier.focusRequester(applyFocus),
                    )
                    TvButton("Clear", onClick = onClear)
                    TvButton("Cancel", onClick = onDismiss, style = TvButtonStyle.Subtle)
                }
                LazyColumn(Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item(key = "genres") {
                        FacetSection("Genres (all must match)", facets.genres.map { it.value }.filter { it.isNotBlank() }, genres::contains) { v ->
                            genres = if (v in genres) genres - v else genres + v
                        }
                    }
                    item(key = "country") {
                        FacetSection("Country", facets.countries.map { it.value }.filter { it.isNotBlank() }, { it == country }) { v ->
                            country = if (country == v) null else v
                        }
                    }
                    item(key = "language") {
                        FacetSection("Language", facets.languages.map { it.value }.filter { it.isNotBlank() }, { it == language }) { v ->
                            language = if (language == v) null else v
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { applyFocus.requestFocus() } }
    }
}

@Composable
private fun FacetSection(title: String, values: List<String>, isSelected: (String) -> Boolean, onToggle: (String) -> Unit) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.TextMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.take(MAX_FACET_CHIPS).forEach { v ->
                val selected = isSelected(v)
                TvButton(
                    text = if (selected) "✓ $v" else v,
                    onClick = { onToggle(v) },
                    style = if (selected) TvButtonStyle.Primary else TvButtonStyle.Secondary,
                )
            }
        }
    }
}
