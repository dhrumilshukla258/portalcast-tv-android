package com.portalcast.tv.ui.discover

import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.data.model.DiscoverFacets
import com.portalcast.tv.data.model.DiscoverQuery
import com.portalcast.tv.data.model.FacetValue
import com.portalcast.tv.ui.components.PanelRow
import com.portalcast.tv.ui.components.PanelSection
import com.portalcast.tv.ui.components.SidePanel
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.components.TvButtonStyle
import com.portalcast.tv.ui.theme.AppColors
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

/**
 * Discover filters in the Letterboxd style: a row of selectors in the header
 * ("Genre ▾  Language ▾  Country ▾  Mood ▾"), each opening a short list, applied the moment
 * something is picked; no Apply button, no modal. One choice per selector, except Genre: any
 * number, each required or left out ("not Horror"), as on the web.
 */
enum class FilterDim(val label: String) { Genre("Genre"), Language("Language"), Country("Country"), Channel("Channel / Platform"), Mood("Mood"), Imdb("IMDb rating") }

internal fun DiscoverQuery?.valueOf(dim: FilterDim): String? = when (dim) {
    FilterDim.Genre -> this?.let { q -> (q.genres + q.notGenres.map { "not $it" }).takeIf { it.isNotEmpty() }?.joinToString(", ") }
    FilterDim.Language -> this?.language
    FilterDim.Country -> this?.country
    FilterDim.Channel -> this?.network
    FilterDim.Mood -> this?.theme
    FilterDim.Imdb -> this?.minImdb
}

internal fun DiscoverQuery.with(dim: FilterDim, value: String?): DiscoverQuery = when (dim) {
    FilterDim.Genre -> copy(genres = listOfNotNull(value), notGenres = emptyList())
    FilterDim.Language -> copy(language = value)
    FilterDim.Country -> copy(country = value)
    FilterDim.Channel -> copy(network = value)
    FilterDim.Mood -> copy(theme = value)
    FilterDim.Imdb -> copy(minImdb = value)
}

internal fun DiscoverFacets.optionsFor(dim: FilterDim): List<FacetValue> = when (dim) {
    FilterDim.Genre -> genres.filter { it.value.isNotBlank() && !it.value.equals("Uncategorized", ignoreCase = true) }
    FilterDim.Language -> languages.filter { it.value.isNotBlank() }
    FilterDim.Country -> countries.filter { it.value.isNotBlank() }
    FilterDim.Channel -> networks.filter { it.value.isNotBlank() }
    FilterDim.Mood -> themes.filter { it.value.isNotBlank() }
    FilterDim.Imdb -> imdbRatings.filter { it.value.isNotBlank() }
}

/** How many filters are set; each genre counts (the Filters button's "· n"). */
internal fun DiscoverQuery?.activeCount(): Int = if (this == null) 0 else
    genres.size + notGenres.size + FilterDim.entries.count { it != FilterDim.Genre && valueOf(it) != null }

/** "hi" -> "Hindi"; unknown codes stay as they are. */
internal fun languageName(code: String): String {
    // TMDB's own codes that are not ISO 639: "cn" is Cantonese (Mandarin/Chinese is "zh").
    when (code.lowercase()) { "cn" -> return "Cantonese"; "xx" -> return "No language" }
    if (code.length !in 2..3) return code
    val name = Locale(code).getDisplayLanguage(Locale.ENGLISH)
    return if (name.isBlank() || name.equals(code, ignoreCase = true)) code else name
}

private fun displayValue(dim: FilterDim, value: String): String = when (dim) {
    FilterDim.Language -> languageName(value)
    FilterDim.Imdb -> "$value+"
    else -> value
}

/** The selector buttons for the header. [focus] lets the screen put focus back after a pick. */
@Composable
internal fun FilterSelectors(
    facets: DiscoverFacets,
    query: DiscoverQuery?,
    focus: Map<FilterDim, FocusRequester>,
    onOpen: (FilterDim) -> Unit,
    onClear: () -> Unit,
    /** Window x of each selector, so its list can open right under it. */
    onPositioned: (FilterDim, Float) -> Unit = { _, _ -> },
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (dim in FilterDim.entries) {
            if (facets.optionsFor(dim).isEmpty()) continue
            val value = query.valueOf(dim)
            TvButton(
                text = (value?.let { displayValue(dim, it) } ?: dim.label) + "  ▾",
                onClick = { onOpen(dim) },
                style = if (value != null) TvButtonStyle.Primary else TvButtonStyle.Subtle,
                modifier = Modifier
                    .focusRequester(focus.getValue(dim))
                    .onGloballyPositioned { onPositioned(dim, it.positionInWindow().x) },
            )
        }
        if (FilterDim.entries.any { query.valueOf(it) != null }) {
            TvButton(text = "Clear", onClick = onClear, style = TvButtonStyle.Subtle)
        }
    }
}

/**
 * The values of one selector, in the side panel (SidePanel) that Filters uses. Center picks;
 * Back closes. "Any" clears this selector only.
 */
@Composable
internal fun BoxScope.FilterDropdown(
    dim: FilterDim,
    facets: DiscoverFacets,
    query: DiscoverQuery?,
    /** Measured height of the top bar (px): the list opens right under it. */
    topPx: Int,
    /** x (px, in the screen's Box) of the selector that opened it: the list lines up with it. */
    leftPx: Int,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    /** Genre: a press cycles the value (required, left out, off) and the list stays open. */
    onCycle: ((String) -> Unit)? = null,
    /** False when it opens in place of the Filters menu (SidePanel slideIn). */
    slideIn: Boolean = true,
) {
    val options = facets.optionsFor(dim)
    val current = query.valueOf(dim)
    val included = if (onCycle != null) query?.genres.orEmpty() else emptyList()
    val excluded = if (onCycle != null) query?.notGenres.orEmpty() else emptyList()
    val selectedIndex = (if (onCycle != null) options.indexOfFirst { it.value in included || it.value in excluded } else options.indexOfFirst { it.value == current })
        .let { if (it < 0) 0 else it + 1 } // +1 for "Any"
    val initial = remember { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 3).coerceAtLeast(0))
    val numbers = remember { NumberFormat.getIntegerInstance(Locale.US) }

    BackHandler(onBack = onDismiss)
    LaunchedEffect(dim) {
        repeat(20) {
            if (runCatching { initial.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }

    // The full-height side panel (owner: no small floating windows), from the top of the screen.
    SidePanel(
        title = dim.label,
        subtitle = if (onCycle != null) "OK once to include a genre, again to leave it out" else null,
        onDismiss = onDismiss,
        slideIn = slideIn,
    ) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(listOf<FacetValue?>(null) + options, key = { i, o -> o?.value ?: "any-$i" }) { i, o ->
                val out = o != null && o.value in excluded
                val selected = (o == null && current == null) || (o != null && (if (onCycle != null) o.value in included else o.value == current))
                PanelRow(
                    label = o?.let { if (out) "Not " + displayValue(dim, it.value) else displayValue(dim, it.value) } ?: "Any",
                    value = o?.count?.toInt()?.takeIf { it > 0 }?.let { numbers.format(it) },
                    selected = selected,
                    // A block sign when left out (genres).
                    leading = if (out) ({ BlockIcon(Modifier.size(20.dp)) }) else null,
                    onClick = { if (o != null && onCycle != null) onCycle(o.value) else onPick(o?.value) },
                    modifier = if (i == selectedIndex) Modifier.focusRequester(initial) else Modifier,
                )
            }
        }
    }
}

/** The "not" sign: a circle with a slash, in the current content colour. */
@Composable
private fun BlockIcon(modifier: Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier) {
        val stroke = size.minDimension * 0.12f
        val r = size.minDimension / 2 - stroke / 2
        drawCircle(color, radius = r, style = Stroke(stroke))
        val d = r * 0.7071f
        drawLine(color, Offset(center.x - d, center.y + d), Offset(center.x + d, center.y - d), strokeWidth = stroke)
    }
}

/**
 * The Filters panel (owner, 2026-10-22: one row under the tabs, the same as the phone): a
 * full-height panel along the right edge, under the Filters button, listing each selector with
 * its value, then "Browse
 * by category" (Movies / Series) and "Clear filters". A selector opens its values
 * ([FilterDropdown]); Back closes the panel. [focus] holds one requester per selector, so
 * focus comes back to the one whose values were just closed.
 */
@Composable
internal fun BoxScope.FilterMenu(
    facets: DiscoverFacets,
    query: DiscoverQuery?,
    focus: Map<FilterDim, FocusRequester>,
    topPx: Int,
    onOpen: (FilterDim) -> Unit,
    onClear: () -> Unit,
    browseLabel: String?,
    onBrowse: (() -> Unit)?,
    onDismiss: () -> Unit,
    /** Movies / Series: the NAS library's titles of that type. */
    onMyLibrary: (() -> Unit)? = null,
    /** False when it comes back in place of a selector's values (SidePanel slideIn). */
    slideIn: Boolean = true,
) {
    val dims = FilterDim.entries.filter { facets.optionsFor(it).isNotEmpty() }
    val first = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) {
        repeat(20) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }
    val anySet = FilterDim.entries.any { query.valueOf(it) != null }
    SidePanel(
        title = "Filters",
        subtitle = if (anySet) "Picks apply at once" else null,
        onDismiss = onDismiss,
        slideIn = slideIn,
    ) {
        // Scrolls: on a TV screen the Sources at the bottom were below its edge, out of reach
        // (owner 2026-11-04, photos: Series showed no Sources, Movies half of My library).
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            dims.forEachIndexed { i, dim ->
                val value = query.valueOf(dim)
                PanelRow(
                    label = dim.label,
                    value = value?.let { displayValue(dim, it) } ?: "Any",
                    valueSet = value != null,
                    chevron = true,
                    onClick = { onOpen(dim) },
                    modifier = Modifier.focusRequester(focus.getValue(dim)).let { if (i == 0) it.focusRequester(first) else it },
                )
            }
            // Where titles come from, under their own heading after the filters (owner 2026-11-04;
            // the same as the web's Filters sheet).
            if (onMyLibrary != null || (onBrowse != null && browseLabel != null)) PanelSection("Sources")
            if (onMyLibrary != null) {
                PanelRow(label = "My library", detail = "Files on your NAS", chevron = true, onClick = onMyLibrary)
            }
            if (onBrowse != null && browseLabel != null) {
                PanelRow(label = browseLabel, detail = "Your provider's own categories", chevron = true, onClick = onBrowse)
            }
            if (anySet) {
                Spacer(Modifier.height(12.dp))
                PanelRow(label = "Clear filters", onClick = onClear)
            }
        }
    }
}
