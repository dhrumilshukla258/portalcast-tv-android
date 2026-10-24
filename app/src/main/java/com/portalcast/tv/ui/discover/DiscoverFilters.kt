package com.portalcast.tv.ui.discover

import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
enum class FilterDim(val label: String) { Genre("Genre"), Language("Language"), Country("Country"), Channel("Channel / Platform"), Mood("Mood") }

internal fun DiscoverQuery?.valueOf(dim: FilterDim): String? = when (dim) {
    FilterDim.Genre -> this?.let { q -> (q.genres + q.notGenres.map { "not $it" }).takeIf { it.isNotEmpty() }?.joinToString(", ") }
    FilterDim.Language -> this?.language
    FilterDim.Country -> this?.country
    FilterDim.Channel -> this?.network
    FilterDim.Mood -> this?.theme
}

internal fun DiscoverQuery.with(dim: FilterDim, value: String?): DiscoverQuery = when (dim) {
    FilterDim.Genre -> copy(genres = listOfNotNull(value), notGenres = emptyList())
    FilterDim.Language -> copy(language = value)
    FilterDim.Country -> copy(country = value)
    FilterDim.Channel -> copy(network = value)
    FilterDim.Mood -> copy(theme = value)
}

internal fun DiscoverFacets.optionsFor(dim: FilterDim): List<FacetValue> = when (dim) {
    FilterDim.Genre -> genres.filter { it.value.isNotBlank() && !it.value.equals("Uncategorized", ignoreCase = true) }
    FilterDim.Language -> languages.filter { it.value.isNotBlank() }
    FilterDim.Country -> countries.filter { it.value.isNotBlank() }
    FilterDim.Channel -> networks.filter { it.value.isNotBlank() }
    FilterDim.Mood -> themes.filter { it.value.isNotBlank() }
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

private fun displayValue(dim: FilterDim, value: String): String = if (dim == FilterDim.Language) languageName(value) else value

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
 * The open list for one selector. Drawn over the hero, top-left, like a dropdown. Center
 * picks and closes; Back closes. "Any" clears this selector only.
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

    // A full-height panel along the right edge, under the Filters button, from under the top bar
    // to the bottom of the screen (owner: no small floating windows). Solid: it sits over the hero.
    Column(
        Modifier
            .align(Alignment.TopEnd)
            .padding(top = with(LocalDensity.current) { topPx.toDp() })
            .fillMaxHeight()
            .width(460.dp)
            .background(AppColors.Background)
            .border(1.dp, AppColors.GlassBorder)
            .padding(start = 16.dp, end = 32.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Text(
            dim.label,
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextMuted,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = if (onCycle != null) 2.dp else 8.dp),
        )
        if (onCycle != null) {
            Text(
                "OK once to include a genre, again to leave it out",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextMuted,
                modifier = Modifier.padding(start = 10.dp, bottom = 8.dp),
            )
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(listOf<FacetValue?>(null) + options, key = { i, o -> o?.value ?: "any-$i" }) { i, o ->
                val out = o != null && o.value in excluded
                val selected = (o == null && current == null) || (o != null && (if (onCycle != null) o.value in included else o.value == current))
                Option(
                    label = o?.let { if (out) "Not " + displayValue(dim, it.value) else displayValue(dim, it.value) } ?: "Any",
                    count = o?.count?.toInt()?.takeIf { it > 0 }?.let { numbers.format(it) },
                    selected = selected,
                    excluded = out,
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

@Composable
private fun Option(label: String, count: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, excluded: Boolean = false) {
    Box(modifier.fillMaxWidth()) {
        TvButton(
            // A tick when chosen, a block sign when left out (genres), an empty slot otherwise,
            // so the labels line up.
            leading = {
                when {
                    selected -> Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    excluded -> BlockIcon(Modifier.size(20.dp))
                    else -> Spacer(Modifier.size(20.dp))
                }
            },
            text = buildString {
                append(label)
                if (count != null) append("   ·  ").append(count)
            },
            onClick = onClick,
            style = if (selected) TvButtonStyle.Primary else if (excluded) TvButtonStyle.Secondary else TvButtonStyle.Subtle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.width(0.dp))
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
    Column(
        Modifier
            .align(Alignment.TopEnd)
            .padding(top = with(LocalDensity.current) { topPx.toDp() })
            .fillMaxHeight()
            .width(460.dp)
            .background(AppColors.Background)
            .border(1.dp, AppColors.GlassBorder)
            .padding(start = 16.dp, end = 32.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Filters",
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextMuted,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 6.dp),
        )
        dims.forEachIndexed { i, dim ->
            val value = query.valueOf(dim)
            TvButton(
                text = dim.label + "   ·   " + (value?.let { displayValue(dim, it) } ?: "Any") + "   ›",
                onClick = { onOpen(dim) },
                style = if (value != null) TvButtonStyle.Primary else TvButtonStyle.Subtle,
                modifier = Modifier.fillMaxWidth().focusRequester(focus.getValue(dim)).let { if (i == 0) it.focusRequester(first) else it },
            )
        }
        if (onMyLibrary != null) {
            TvButton(text = "My library (NAS files)   ›", onClick = onMyLibrary, style = TvButtonStyle.Subtle, modifier = Modifier.fillMaxWidth())
        }
        if (onBrowse != null && browseLabel != null) {
            TvButton(text = "$browseLabel   ›", onClick = onBrowse, style = TvButtonStyle.Subtle, modifier = Modifier.fillMaxWidth())
        }
        if (FilterDim.entries.any { query.valueOf(it) != null }) {
            TvButton(text = "Clear filters", onClick = onClear, style = TvButtonStyle.Subtle, modifier = Modifier.fillMaxWidth())
        }
    }
}
