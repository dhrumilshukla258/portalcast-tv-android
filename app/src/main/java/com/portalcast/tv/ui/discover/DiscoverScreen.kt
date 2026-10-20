@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)

package com.portalcast.tv.ui.discover

import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.portalcast.tv.ui.components.keepColumn
import com.portalcast.tv.ui.components.gridItem
import com.portalcast.tv.ui.components.rememberGridFocus
import com.portalcast.tv.ui.components.FeedAmbient
import androidx.compose.foundation.border
import androidx.compose.ui.window.Dialog
import com.portalcast.tv.ui.components.rememberedIn
import com.portalcast.tv.ui.components.rememberFocusMemory
import com.portalcast.tv.ui.components.focusMemory
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import com.portalcast.tv.data.model.DiscoverQuery
import com.portalcast.tv.data.model.DiscoverFacets
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.ui.components.ErrorText
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.components.TvButtonStyle
import com.portalcast.tv.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/** How close to a row's / the grid's end (in items) the next page is requested. */
private const val PAGE_AHEAD = 8

/** Share of the screen height the hero (top bar + title/meta/overview) takes; rows scroll below it. */
private const val HERO_FRACTION = 0.45f

/** Row title height; fixed so every row has the same geometry under the hero. */
private val RowTitleHeight = 34.dp

/**
 * The Discover tab's content pane, hosted by HomeScreen inside its nav rail.
 *
 * Layout: a fixed hero in the top [HERO_FRACTION] of the screen (the focused item's
 * backdrop, title, meta line and overview), the rows in a LazyColumn below it. The
 * focused row always scrolls to sit directly under the hero ([PivotRowToTop]).
 * Focus changes only write [HeroFocus]; nothing but the hero reads it.
 *
 * Items handed to the callbacks are regular list items ready for the detail screens:
 * Discover items must already be resolved through `DiscoverRepository.openItem`
 * (raw id, `categoryId` stamped). Continue Watching entries also open the detail
 * screen, which owns Resume.
 */
@Composable
fun DiscoverScreen(
    onOpenMovie: (MediaItem) -> Unit,
    onOpenSeries: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    /** Kept for Home's call site; Discover has no Search button (the rail has Search). */
    @Suppress("UNUSED_PARAMETER") onOpenSearch: () -> Unit = {},
    /** True when coming back from a detail/player: put focus back on the last card. */
    restoreFocus: Boolean = false,
) {
    val container = LocalContext.current.appContainer
    val vm: DiscoverViewModel = viewModel(
        factory = viewModelFactory { initializer { DiscoverViewModel(container.discoverRepository, container.userRepository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val serverUrl by container.sessionStore.serverUrl.collectAsStateWithLifecycle()
    // Letterboxd-style selectors (DiscoverFilters.kt): which one is open, and where focus
    // goes back to when it closes.
    var openFilter by remember { mutableStateOf<FilterDim?>(null) }
    var returnFocusTo by remember { mutableStateOf<FilterDim?>(null) }
    val selectorFocus = remember { FilterDim.entries.associateWith { FocusRequester() } }
    // Measured height of the top bar: the open selector list is drawn right under it.
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val selectorX = remember { mutableStateMapOf<FilterDim, Float>() }
    var screenX by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(returnFocusTo) {
        val dim = returnFocusTo ?: return@LaunchedEffect
        repeat(20) { if (runCatching { selectorFocus.getValue(dim).requestFocus() }.isSuccess) return@LaunchedEffect; delay(40) }
    }
    val hero = remember { HeroFocus() }

    LaunchedEffect(Unit) { vm.onScreenShown() }

    LaunchedEffect(state.navigation) {
        val nav = state.navigation ?: return@LaunchedEffect
        vm.onNavigationHandled()
        if (nav.isSeries) onOpenSeries(nav.item) else onOpenMovie(nav.item)
    }
    LaunchedEffect(state.openError) {
        if (state.openError != null) {
            delay(4_000)
            vm.clearOpenError()
        }
    }
    BackHandler(enabled = state.grid != null) { vm.clearFilter() }

    // Until a card is focused the hero shows the first item of the first row.
    val firstItem = remember(state.rows, state.continueWatching) {
        state.visibleRows.firstOrNull { it.items.isNotEmpty() }?.items?.firstOrNull()
            ?: state.continueWatching.firstOrNull()?.target
    }
    LaunchedEffect(firstItem) { hero.fallback = firstItem }
    // A type switch reloads every row: drop the stale focused item.
    LaunchedEffect(state.type) { hero.item = null }

    Box(modifier.fillMaxSize().background(AppColors.Background).onGloballyPositioned { screenX = it.positionInWindow().x }) {
        HeroBackdrop(
            focus = hero,
            serverUrl = serverUrl,
            modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.72f).fillMaxHeight(0.68f),
        )

        Column(Modifier.fillMaxSize()) {
            val grid = state.grid
            if (grid != null) {
                TopBar(
                    type = state.type,
                    facets = state.facets,
                    query = grid.query,
                    selectorFocus = selectorFocus,
                    onType = vm::setType,
                    onOpenFilter = { openFilter = it },
                    onClear = vm::clearFilter,
                    onSelectorPositioned = { dim, x -> selectorX[dim] = x },
                    modifier = Modifier.onSizeChanged { topBarHeightPx = it.height },
                )
                FilterGridContent(grid, serverUrl, hero, onOpen = vm::open, onNearEnd = vm::onGridNearEnd)
            } else {
                Column(Modifier.fillMaxWidth().fillMaxHeight(HERO_FRACTION)) {
                    TopBar(
                        type = state.type,
                        facets = state.facets,
                        query = null,
                        selectorFocus = selectorFocus,
                        onType = vm::setType,
                        onOpenFilter = { openFilter = it },
                        onClear = vm::clearFilter,
                        onSelectorPositioned = { dim, x -> selectorX[dim] = x },
                        modifier = Modifier.onSizeChanged { topBarHeightPx = it.height },
                    )
                    HeroInfo(hero, Modifier.fillMaxWidth().weight(1f), serverUrl)
                }
                RowsContent(state, serverUrl, vm, hero, restoreFocus, Modifier.fillMaxWidth().weight(1f))
            }
        }

        if (state.resolving) {
            Text(
                "Opening…",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(AppColors.Surface, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = AppColors.Text,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        openFilter?.let { dim ->
            FilterDropdown(
                dim = dim,
                facets = state.facets,
                query = state.grid?.query,
                topPx = topBarHeightPx,
                leftPx = ((selectorX[dim] ?: screenX) - screenX).toInt().coerceAtLeast(0),
                onPick = { value ->
                    val base = state.grid?.query ?: DiscoverQuery(type = state.type)
                    vm.applyFilter(base.with(dim, value))
                    openFilter = null
                    returnFocusTo = dim
                },
                onDismiss = { openFilter = null; returnFocusTo = dim },
            )
        }
        state.openError?.let { msg ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
                    .background(AppColors.Surface, RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) { ErrorText(msg) }
        }
    }

    state.variants?.let { variants ->
        VariantPickerDialog(variants, serverUrl, onChoose = vm::chooseVariant, onDismiss = vm::dismissVariants)
    }
}

@Composable
private fun TopBar(
    type: String?,
    facets: DiscoverFacets,
    query: DiscoverQuery?,
    selectorFocus: Map<FilterDim, FocusRequester>,
    onType: (String?) -> Unit,
    onOpenFilter: (FilterDim) -> Unit,
    onClear: () -> Unit,
    onSelectorPositioned: (FilterDim, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 20.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Discover", style = MaterialTheme.typography.headlineSmall, color = AppColors.Text)
            Spacer(Modifier.width(24.dp))
            listOf<Pair<String?, String>>(null to "All", "movie" to "Movies", "series" to "Series").forEach { (value, label) ->
                TvButton(
                    text = label,
                    onClick = { onType(value) },
                    style = if (type == value) TvButtonStyle.Primary else TvButtonStyle.Subtle,
                )
            }
        }
        // Letterboxd's "Browse by" line: the selectors on a row of their own, so a long
        // value ("United States ▾") never squeezes the row above into wrapped letters.
        FilterSelectors(facets = facets, query = query, focus = selectorFocus, onOpen = onOpenFilter, onClear = onClear, onPositioned = onSelectorPositioned)
    }
}

@Composable
private fun RowsContent(
    state: DiscoverUiState,
    serverUrl: String?,
    vm: DiscoverViewModel,
    hero: HeroFocus,
    restoreFocus: Boolean,
    modifier: Modifier,
) {
    val rows = state.visibleRows
    val cw = state.continueWatching
    val ambientItems = remember(rows) { rows.flatMap { it.items.take(12) } }
    FeedAmbient(serverUrl, ambientItems)
    if (rows.isEmpty() && cw.isEmpty()) {
        Box(modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
            when {
                state.loading -> Text("Loading…", color = AppColors.TextMuted, style = MaterialTheme.typography.bodyLarge)
                state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ErrorText(state.error)
                    TvButton("Retry", onClick = vm::retry, style = TvButtonStyle.Primary)
                }
                else -> Text(state.emptyHint ?: DiscoverViewModel.EMPTY_HINT, color = AppColors.TextMuted, style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }
    // Stepping Down brings a whole new row on screen; with the default nested prefetch (2
    // cards) its other ~6 cards were composed inside one frame (up to 48 ms on the TV).
    // Prefetching a full row's worth spreads that over idle frame time instead.
    val listState = rememberLazyListState(prefetchStrategy = remember { LazyListPrefetchStrategy(nestedPrefetchItemCount = 8) })
    val pivot = remember(listState) { PivotRowToTop { listState.layoutInfo.viewportSize.height } }
    val cardPad = with(LocalDensity.current) { remember { PadRectHorizontally(40.dp.toPx()) } }
    // Stable per-screen lambdas: cards get the same instances every recomposition.
    val onFocusItem: (MediaItem) -> Unit = remember(hero) { { item -> hero.item = item } }
    val onOpen: (MediaItem) -> Unit = remember(vm) { vm::open }
    val onRowNearEnd: (String) -> Unit = remember(vm) { vm::onRowNearEnd }
    // Back from a title opened here: focus the card it was opened from (Home otherwise fell back
    // to the rail, which looked like Back "opening the side bar").
    val restoreRequester = remember { FocusRequester() }
    val restoreKey = remember { if (restoreFocus) vm.lastFocusedKey else null }
    LaunchedEffect(restoreKey) {
        if (restoreKey == null) return@LaunchedEffect
        repeat(20) {
            withFrameNanos { }
            if (runCatching { restoreRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    val cardModifier: (String) -> Modifier = { key ->
        val base = Modifier.onFocusChanged { if (it.isFocused) vm.lastFocusedKey = key }
        if (key == restoreKey) base.focusRequester(restoreRequester) else base
    }
    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
        // Bottom room so the last rows can still scroll up under the hero.
        contentPadding = PaddingValues(bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (cw.isNotEmpty()) {
            item(key = "cw") {
                ContinueWatchingRow(
                    cw,
                    serverUrl,
                    onOpen = vm::openContinueWatching,
                    onRemove = vm::removeContinue,
                    onFocusItem = onFocusItem,
                    cardPad = cardPad,
                    cardModifier = cardModifier,
                    modifier = Modifier.bringIntoViewResponder(pivot),
                )
            }
        }
        if (state.error != null) {
            item(key = "error") { ErrorText(state.error, Modifier.padding(horizontal = 48.dp)) }
        } else if (state.emptyHint != null) {
            item(key = "hint") {
                Text(state.emptyHint, color = AppColors.TextMuted, modifier = Modifier.padding(horizontal = 48.dp))
            }
        }
        items(rows, key = { it.key }) { row ->
            LaunchedEffect(row.key) { vm.onRowVisible(row.key) }
            MediaRow(
                row = row,
                serverUrl = serverUrl,
                onOpen = onOpen,
                onFocusItem = onFocusItem,
                onRowNearEnd = onRowNearEnd,
                cardPad = cardPad,
                cardModifier = cardModifier,
                modifier = Modifier.bringIntoViewResponder(pivot),
            )
        }
    }
}

@Composable
private fun RowTitle(text: String) {
    Box(Modifier.height(RowTitleHeight).padding(start = 48.dp), contentAlignment = Alignment.BottomStart) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = AppColors.Text, maxLines = 1)
    }
}

@Composable
private fun ContinueWatchingRow(
    entries: List<ContinueEntry>,
    serverUrl: String?,
    onOpen: (ContinueEntry) -> Unit,
    onRemove: (ContinueEntry) -> Unit,
    onFocusItem: (MediaItem) -> Unit,
    cardPad: PadRectHorizontally,
    cardModifier: (String) -> Modifier,
    modifier: Modifier,
) {
    val memory = rememberFocusMemory()
    // Hold Center on a card: confirm, then remove it from Continue Watching.
    var confirm by remember { mutableStateOf<ContinueEntry?>(null) }
    confirm?.let { entry ->
        RemoveContinueDialog(entry, onRemove = { confirm = null; onRemove(entry) }, onDismiss = { confirm = null })
    }
    Column(modifier) {
        RowTitle("Continue Watching")
        LazyRow(
            modifier = Modifier.focusMemory(memory),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(entries, key = { it.key }) { e ->
                PosterCard(
                    title = e.title,
                    subtitle = e.subtitle,
                    imagePath = e.imagePath,
                    serverUrl = serverUrl,
                    progressPercent = e.progressPercent,
                    onClick = { onOpen(e) },
                    onLongClick = { confirm = e },
                    onFocused = { onFocusItem(e.target) },
                    modifier = cardModifier("cw|${e.key}").rememberedIn(memory).bringIntoViewResponder(cardPad),
                )
            }
        }
    }
}

@Composable
private fun RemoveContinueDialog(entry: ContinueEntry, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(entry.key) { repeat(10) { withFrameNanos { }; if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(520.dp)
                .background(AppColors.Background.copy(alpha = 0.97f), RoundedCornerShape(16.dp))
                .border(1.dp, AppColors.GlassBorder, RoundedCornerShape(16.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(entry.title, style = MaterialTheme.typography.titleLarge, color = AppColors.Text, maxLines = 2)
            Text("Remove it from Continue Watching? Your place in it is forgotten.", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Remove", onClick = onRemove, style = TvButtonStyle.Primary, modifier = Modifier.focusRequester(first))
                TvButton("Cancel", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun MediaRow(
    row: DiscoverRow,
    serverUrl: String?,
    onOpen: (MediaItem) -> Unit,
    onFocusItem: (MediaItem) -> Unit,
    onRowNearEnd: (String) -> Unit,
    cardPad: PadRectHorizontally,
    cardModifier: (String) -> Modifier,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val nearEnd by rememberUpdatedState(onRowNearEnd)
    LaunchedEffect(listState, row.key) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.distinctUntilChanged().collect { (last, total) ->
            if (total > 0 && last >= total - PAGE_AHEAD) nearEnd(row.key)
        }
    }
    val memory = rememberFocusMemory()
    Column(modifier) {
        RowTitle(row.title)
        if (!row.loaded) {
            PlaceholderRow()
            return@Column
        }
        LazyRow(
            state = listState,
            modifier = Modifier.focusMemory(memory),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(row.items, key = { it.id }) { item ->
                PosterCard(
                    title = item.displayTitle,
                    subtitle = null,
                    imagePath = item.screenshotUri,
                    serverUrl = serverUrl,
                    onClick = { onOpen(item) },
                    onFocused = { onFocusItem(item) },
                    channel = item.network,
                    channelLogo = item.networkLogo,
                    modifier = cardModifier("${row.key}|${item.id}").rememberedIn(memory).bringIntoViewResponder(cardPad),
                )
            }
        }
    }
}

@Composable
private fun PlaceholderRow() {
    Row(
        Modifier.padding(horizontal = 48.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(7) {
            Box(
                Modifier
                    .width(PosterWidth)
                    .posterAspect()
                    .background(AppColors.Surface, RoundedCornerShape(10.dp)),
            )
        }
    }
}

@Composable
private fun FilterGridContent(
    grid: FilterGrid,
    serverUrl: String?,
    hero: HeroFocus,
    onOpen: (MediaItem) -> Unit,
    onNearEnd: () -> Unit,
) {
    val label = listOfNotNull(
        grid.query.genres.takeIf { it.isNotEmpty() }?.joinToString(" + "),
        grid.query.country,
        grid.query.language,
    ).joinToString(" · ")
    Column(Modifier.fillMaxSize()) {
        Text(
            if (grid.loading) label else "$label  (${grid.total})",
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextMuted,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
        )
        when {
            grid.loading -> Text("Loading…", color = AppColors.TextMuted, modifier = Modifier.padding(48.dp))
            grid.error != null -> ErrorText(grid.error, Modifier.padding(48.dp))
            grid.items.isEmpty() -> Text("Nothing matches these filters.", color = AppColors.TextMuted, modifier = Modifier.padding(48.dp))
            else -> {
                val gridState = rememberLazyGridState()
                val nearEnd by rememberUpdatedState(onNearEnd)
                LaunchedEffect(gridState) {
                    snapshotFlow {
                        val info = gridState.layoutInfo
                        (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
                    }.distinctUntilChanged().collect { (last, total) ->
                        if (total > 0 && last >= total - PAGE_AHEAD) nearEnd()
                    }
                }
                val memory = rememberFocusMemory()
                val gridFocus = rememberGridFocus(gridState)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(PosterWidth + 16.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().keepColumn(gridFocus, grid.items.size).focusMemory(memory),
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(grid.items, key = { _, it -> it.id }) { i, item ->
                        PosterCard(
                            title = item.displayTitle,
                            subtitle = item.displayYear,
                            imagePath = item.screenshotUri,
                            serverUrl = serverUrl,
                            onClick = { onOpen(item) },
                            onFocused = { hero.item = item },
                            channel = item.network,
                            channelLogo = item.networkLogo,
                            modifier = Modifier.rememberedIn(memory).gridItem(gridFocus, i),
                        )
                    }
                }
            }
        }
    }
}
