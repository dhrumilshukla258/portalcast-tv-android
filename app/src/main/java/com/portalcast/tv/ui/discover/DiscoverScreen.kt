@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)

package com.portalcast.tv.ui.discover

import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.portalcast.tv.ui.components.keepColumn
import com.portalcast.tv.ui.components.gridItem
import com.portalcast.tv.ui.components.rememberGridFocus
import com.portalcast.tv.ui.components.revealWithTitle
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
import androidx.compose.foundation.lazy.itemsIndexed
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
// 0.42, posters 118 dp and 8 dp row padding: a row (heading, poster, title under it) is about
// 262 dp and fits the ~271 dp left under the hero; at 0.45 / 130 dp the titles fell off the
// bottom of the screen (owner, 2026-10-22).
private const val HERO_FRACTION = 0.42f

/** Row title height; fixed so every row has the same geometry under the hero. */
private val RowTitleHeight = 34.dp

/**
 * The Discover tab's content pane, hosted by HomeScreen under its top tabs.
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
    /** Kept for Home's call site; Discover has no Search button (the tabs have Search). */
    @Suppress("UNUSED_PARAMETER") onOpenSearch: () -> Unit = {},
    /** True when coming back from a detail/player: put focus back on the last card. */
    restoreFocus: Boolean = false,
    /** "movie" / "series": the Movies or Series tab (one type, no type switch). Null: Home. */
    fixedType: String? = null,
    /** Opens the per-category browse (Movies / Series tabs). */
    onOpenCategories: (() -> Unit)? = null,
    /** Home's Movies · Series · People chips (HomeScreen), first in the one row under the tabs. */
    sectionChips: (@Composable () -> Unit)? = null,
    /** Movies / Series: the NAS library's titles (from the Filters panel). */
    onOpenMyLibrary: (() -> Unit)? = null,
) {
    val container = LocalContext.current.appContainer
    val vm: DiscoverViewModel = viewModel(
        key = "discover:${fixedType ?: "all"}",
        factory = viewModelFactory { initializer { DiscoverViewModel(container.discoverRepository, container.userRepository, fixedType, container.homeSnapshots) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val serverUrl by container.sessionStore.serverUrl.collectAsStateWithLifecycle()
    // Letterboxd-style selectors (DiscoverFilters.kt): which one is open, and where focus
    // goes back to when it closes.
    var openFilter by remember { mutableStateOf<FilterDim?>(null) }
    // The Filters panel; focus goes back to the Filters button when it closes.
    var filtersOpen by remember { mutableStateOf(false) }
    val filtersButton = remember { FocusRequester() }
    var refocusFilters by remember { mutableStateOf(false) }
    LaunchedEffect(refocusFilters) {
        if (!refocusFilters) return@LaunchedEffect
        repeat(20) { if (runCatching { filtersButton.requestFocus() }.isSuccess) { refocusFilters = false; return@LaunchedEffect }; delay(40) }
        refocusFilters = false
    }
    var returnFocusTo by remember { mutableStateOf<FilterDim?>(null) }
    val selectorFocus = remember { FilterDim.entries.associateWith { FocusRequester() } }
    // Measured height of the top bar: the open selector list is drawn right under it.
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val selectorX = remember { mutableStateMapOf<FilterDim, Float>() }
    var screenX by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(returnFocusTo) {
        val dim = returnFocusTo ?: return@LaunchedEffect
        repeat(20) { if (runCatching { selectorFocus.getValue(dim).requestFocus() }.isSuccess) { returnFocusTo = null; return@LaunchedEffect }; delay(40) }
        returnFocusTo = null
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

    // Until a card is focused the hero shows what the viewer was last watching, with how far in
    // they are (design-system.md, Home), else the first item of the first row.
    val lastWatched = state.continueWatching.firstOrNull()
    val firstItem = remember(state.rows, state.continueWatching) {
        lastWatched?.target ?: state.visibleRows.firstOrNull { it.items.isNotEmpty() }?.items?.firstOrNull()
    }
    LaunchedEffect(firstItem) {
        hero.fallback = firstItem
        hero.fallbackProgress = lastWatched?.let { HeroProgress(it.detail, it.progressPercent) }
    }
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
                    sectionChips = sectionChips,
                    activeFilters = FilterDim.entries.count { grid.query.valueOf(it) != null },
                    filtersFocus = filtersButton,
                    onOpenFilters = { filtersOpen = true },
                    modifier = Modifier.onSizeChanged { topBarHeightPx = it.height },
                )
                FilterGridContent(grid, serverUrl, hero, onOpen = vm::open, onNearEnd = vm::onGridNearEnd)
            } else {
                Column(Modifier.fillMaxWidth().fillMaxHeight(HERO_FRACTION)) {
                    TopBar(
                        sectionChips = sectionChips,
                        activeFilters = FilterDim.entries.count { state.grid?.query.valueOf(it) != null },
                        filtersFocus = filtersButton,
                        onOpenFilters = { filtersOpen = true },
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
        if (filtersOpen && openFilter == null) {
            FilterMenu(
                facets = state.facets,
                query = state.grid?.query,
                focus = selectorFocus,
                topPx = topBarHeightPx,
                onOpen = { openFilter = it },
                onClear = vm::clearFilter,
                browseLabel = if (fixedType != null) "IPTV categories" else null,
                onBrowse = onOpenCategories?.let { open -> { filtersOpen = false; open() } },
                onMyLibrary = onOpenMyLibrary?.let { open -> { filtersOpen = false; open() } },
                onDismiss = { filtersOpen = false; refocusFilters = true },
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
                    // A pick shows its results: the panel closes and focus rests on the Filters
                    // button, the results right under it (the same as the phone).
                    openFilter = null
                    filtersOpen = false
                    refocusFilters = true
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

/**
 * The one row under the tabs (owner, 2026-10-22; the same as the phone): Home's Movies ·
 * Series · People chips on the left, the Filters button on the right ("Filters · 2" when set).
 */
@Composable
private fun TopBar(
    sectionChips: (@Composable () -> Unit)?,
    activeFilters: Int,
    filtersFocus: FocusRequester,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sectionChips?.invoke()
        Spacer(Modifier.weight(1f))
        // Solid, not Subtle: it sits over the hero backdrop, where a transparent button with grey
        // text disappeared on bright images (owner, 2026-10-22).
        TvButton(
            text = if (activeFilters > 0) "Filters · $activeFilters" else "Filters",
            onClick = onOpenFilters,
            style = if (activeFilters > 0) TvButtonStyle.Primary else TvButtonStyle.Secondary,
            modifier = Modifier.focusRequester(filtersFocus),
        )
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
    val onFocusItem: (MediaItem) -> Unit = remember(hero) { { item -> hero.item = item; hero.progress = null } }
    val onFocusContinue: (ContinueEntry) -> Unit = remember(hero) {
        { e -> hero.item = e.target; hero.progress = HeroProgress(e.detail, e.progressPercent) }
    }
    val onOpen: (MediaItem) -> Unit = remember(vm) { vm::open }
    val onRowNearEnd: (String) -> Unit = remember(vm) { vm::onRowNearEnd }
    // Back from a title opened here: focus the card it was opened from (Home otherwise fell back
    // to the tabs, which looked like Back jumping away from where the viewer was).
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
                    onFocusEntry = onFocusContinue,
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
        itemsIndexed(rows, key = { _, it -> it.key }) { i, row ->
            LaunchedEffect(row.key) { vm.onRowVisible(row.key) }
            // The next row's first posters, so they are drawn at once when it scrolls in.
            rows.getOrNull(i + 1)?.let { next -> PrefetchPosters(next.items, serverUrl) }
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
    onFocusEntry: (ContinueEntry) -> Unit,
    cardPad: PadRectHorizontally,
    cardModifier: (String) -> Modifier,
    modifier: Modifier,
) {
    val memory = rememberFocusMemory()
    // Hold Center on a card: the title menu (with Remove); without one, confirm then remove.
    val titleMenu = com.portalcast.tv.ui.components.LocalTitleMenu.current
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
                ContinueCard(
                    title = e.title,
                    detail = e.detail,
                    wideImagePath = e.wideImagePath,
                    posterPath = e.imagePath,
                    serverUrl = serverUrl,
                    progressPercent = e.progressPercent,
                    onClick = { onOpen(e) },
                    onLongClick = titleMenu?.let { open -> { open(com.portalcast.tv.ui.components.TitleMenuTarget(e.target, onRemoveFromContinue = { onRemove(e) })) } } ?: { confirm = e },
                    onFocused = { onFocusEntry(e) },
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
    // The dialog opens while Center is still held (it is a long press): until that key is
    // released, every key is swallowed, or the release itself pressed the focused button.
    var armed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .onPreviewKeyEvent { ev ->
                    if (armed) return@onPreviewKeyEvent false
                    if (ev.type == KeyEventType.KeyUp) armed = true
                    true
                }
                .width(520.dp)
                .background(AppColors.Background.copy(alpha = 0.97f), RoundedCornerShape(16.dp))
                .border(1.dp, AppColors.GlassBorder, RoundedCornerShape(16.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(entry.title, style = MaterialTheme.typography.titleLarge, color = AppColors.Text, maxLines = 2)
            Text("Remove it from Continue Watching? Your place in it is forgotten.", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Cancel is focused first: removing takes a deliberate Left + Center.
                TvButton("Remove", onClick = onRemove, style = TvButtonStyle.Primary)
                TvButton("Cancel", onClick = onDismiss, modifier = Modifier.focusRequester(first))
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
    val titleMenu = com.portalcast.tv.ui.components.LocalTitleMenu.current
    val hidden by com.portalcast.tv.ui.components.HiddenTitles.ids.collectAsStateWithLifecycle()
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
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(row.items.filterNot { com.portalcast.tv.ui.components.titleContentId(it) in hidden }, key = { it.id }) { item ->
                PosterCard(
                    title = item.displayTitle,
                    subtitle = null,
                    imagePath = item.screenshotUri,
                    serverUrl = serverUrl,
                    onClick = { onOpen(item) },
                    onFocused = { onFocusItem(item) },
                    onLongClick = titleMenu?.let { open -> { open(com.portalcast.tv.ui.components.TitleMenuTarget(item)) } },
                    channel = item.network,
                    channelLogo = item.networkLogo,
                    badge = item.localBadge,
                    rating = ratingOrNull(item.ratingBest),
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
    val hidden by com.portalcast.tv.ui.components.HiddenTitles.ids.collectAsStateWithLifecycle()
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
                    // Exactly poster-wide columns: Adaptive made cells wider than the fixed-width
                    // card, which then sat at the left of its cell with uneven gaps.
                    columns = GridCells.FixedSize(PosterWidth),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().keepColumn(gridFocus, grid.items.size).focusMemory(memory),
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    itemsIndexed(grid.items.filterNot { com.portalcast.tv.ui.components.titleContentId(it) in hidden }, key = { _, it -> it.id }) { i, item ->
                        PosterCard(
                            title = item.displayTitle,
                            subtitle = item.displayYear,
                            imagePath = item.screenshotUri,
                            serverUrl = serverUrl,
                            onClick = { onOpen(item) },
                            onFocused = { hero.item = item },
                            onLongClick = com.portalcast.tv.ui.components.LocalTitleMenu.current?.let { open -> { open(com.portalcast.tv.ui.components.TitleMenuTarget(item)) } },
                            channel = item.network,
                            channelLogo = item.networkLogo,
                            badge = item.localBadge,
                            modifier = Modifier.rememberedIn(memory).gridItem(gridFocus, i).revealWithTitle(),
                        )
                    }
                }
            }
        }
    }
}
