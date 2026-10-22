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
import androidx.compose.ui.unit.dp
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
 * ("Genre ▾  Language ▾  Country ▾  Mood ▾"), each opening a short list. One choice per
 * selector, applied the moment it is picked; no Apply button, no modal.
 */
enum class FilterDim(val label: String) { Genre("Genre"), Language("Language"), Country("Country"), Channel("Channel / Platform"), Mood("Mood") }

internal fun DiscoverQuery?.valueOf(dim: FilterDim): String? = when (dim) {
    FilterDim.Genre -> this?.genres?.firstOrNull()
    FilterDim.Language -> this?.language
    FilterDim.Country -> this?.country
    FilterDim.Channel -> this?.network
    FilterDim.Mood -> this?.theme
}

internal fun DiscoverQuery.with(dim: FilterDim, value: String?): DiscoverQuery = when (dim) {
    FilterDim.Genre -> copy(genres = listOfNotNull(value))
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
) {
    val options = facets.optionsFor(dim)
    val current = query.valueOf(dim)
    val selectedIndex = options.indexOfFirst { it.value == current }.let { if (it < 0) 0 else it + 1 } // +1 for "Any"
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

    // A full-height panel along the left edge, from under the top bar to the bottom of the
    // screen (owner: no small floating windows). Solid: it sits over the hero image.
    Column(
        Modifier
            .align(Alignment.TopStart)
            .padding(top = with(LocalDensity.current) { topPx.toDp() })
            .fillMaxHeight()
            .width(460.dp)
            .background(AppColors.Background)
            .border(1.dp, AppColors.GlassBorder)
            .padding(start = 32.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Text(
            dim.label,
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextMuted,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 8.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(listOf<FacetValue?>(null) + options, key = { i, o -> o?.value ?: "any-$i" }) { i, o ->
                val selected = (o == null && current == null) || (o != null && o.value == current)
                Option(
                    label = o?.let { displayValue(dim, it.value) } ?: "Any",
                    count = o?.count?.toInt()?.takeIf { it > 0 }?.let { numbers.format(it) },
                    selected = selected,
                    onClick = { onPick(o?.value) },
                    modifier = if (i == selectedIndex) Modifier.focusRequester(initial) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun Option(label: String, count: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        TvButton(
            text = buildString {
                append(if (selected) "✓  " else "    ")
                append(label)
                if (count != null) append("   ·  ").append(count)
            },
            onClick = onClick,
            style = if (selected) TvButtonStyle.Primary else TvButtonStyle.Subtle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.width(0.dp))
}

/**
 * The Filters panel (owner, 2026-10-22: one row under the tabs, the same as the phone): a
 * full-height panel along the left edge listing each selector with its value, then "Browse
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
            .align(Alignment.TopStart)
            .padding(top = with(LocalDensity.current) { topPx.toDp() })
            .fillMaxHeight()
            .width(460.dp)
            .background(AppColors.Background)
            .border(1.dp, AppColors.GlassBorder)
            .padding(start = 32.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
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
