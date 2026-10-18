@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)

package com.portalcast.tv.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.ui.components.ErrorText
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.components.TvButtonStyle
import com.portalcast.tv.ui.theme.AppColors
import com.portalcast.tv.util.ImageUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/** How close to a row's / the grid's end (in items) the next page is requested. */
private const val PAGE_AHEAD = 8

/**
 * The Discover tab's content pane, hosted by HomeScreen (Phase 2a) inside its nav rail.
 * Phase 2b replaces this body; keep the signature stable.
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
) {
    val container = LocalContext.current.appContainer
    val vm: DiscoverViewModel = viewModel(
        factory = viewModelFactory { initializer { DiscoverViewModel(container.discoverRepository, container.userRepository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val serverUrl by container.sessionStore.serverUrl.collectAsStateWithLifecycle()
    var backdrop by remember { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }

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

    val onFocusItem: (MediaItem) -> Unit = { item ->
        (item.backdropHdPath?.takeIf { it.isNotBlank() } ?: item.backdropPath?.takeIf { it.isNotBlank() })?.let { backdrop = it }
    }

    Box(modifier.fillMaxSize().background(AppColors.Background)) {
        Backdrop(ImageUrl.resolve(serverUrl, backdrop))

        Column(Modifier.fillMaxSize()) {
            TopBar(
                type = state.type,
                filtered = state.grid != null,
                onType = vm::setType,
                onFilters = { showFilters = true },
                onClear = vm::clearFilter,
            )
            val grid = state.grid
            if (grid != null) {
                FilterGridContent(grid, serverUrl, onOpen = vm::open, onFocusItem = onFocusItem, onNearEnd = vm::onGridNearEnd)
            } else {
                RowsContent(state, serverUrl, vm, onFocusItem)
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
        VariantPickerDialog(variants, onChoose = vm::chooseVariant, onDismiss = vm::dismissVariants)
    }
    if (showFilters) {
        FiltersDialog(
            facets = state.facets,
            current = state.grid?.query,
            onApply = { q -> showFilters = false; vm.applyFilter(q) },
            onClear = { showFilters = false; vm.clearFilter() },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun Backdrop(url: String?) {
    if (url == null) return
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.28f,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(AppColors.Background.copy(alpha = 0.4f), AppColors.Background)),
            ),
        )
    }
}

@Composable
private fun TopBar(
    type: String?,
    filtered: Boolean,
    onType: (String?) -> Unit,
    onFilters: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Discover", style = MaterialTheme.typography.headlineMedium, color = AppColors.Text)
        Spacer(Modifier.width(24.dp))
        listOf<Pair<String?, String>>(null to "All", "movie" to "Movies", "series" to "Series").forEach { (value, label) ->
            TvButton(
                text = label,
                onClick = { onType(value) },
                style = if (type == value) TvButtonStyle.Primary else TvButtonStyle.Subtle,
            )
        }
        Spacer(Modifier.width(24.dp))
        TvButton(text = if (filtered) "Filters •" else "Filters", onClick = onFilters)
        if (filtered) TvButton(text = "Clear", onClick = onClear, style = TvButtonStyle.Subtle)
    }
}

@Composable
private fun RowsContent(
    state: DiscoverUiState,
    serverUrl: String?,
    vm: DiscoverViewModel,
    onFocusItem: (MediaItem) -> Unit,
) {
    val rows = state.visibleRows
    val cw = state.continueWatching
    if (rows.isEmpty() && cw.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
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
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (cw.isNotEmpty()) {
            item(key = "cw") {
                ContinueWatchingRow(cw, serverUrl, onOpen = vm::openContinueWatching)
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
                onOpen = vm::open,
                onFocusItem = onFocusItem,
                onNearEnd = { vm.onRowNearEnd(row.key) },
            )
        }
    }
}

@Composable
private fun RowTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = AppColors.Text,
        modifier = Modifier.padding(start = 48.dp, bottom = 10.dp),
    )
}

@Composable
private fun ContinueWatchingRow(entries: List<ContinueEntry>, serverUrl: String?, onOpen: (ContinueEntry) -> Unit) {
    Column {
        RowTitle("Continue Watching")
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(entries, key = { it.key }) { e ->
                PosterCard(
                    title = e.title,
                    subtitle = e.subtitle,
                    imageUrl = ImageUrl.resolve(serverUrl, e.imagePath),
                    progressPercent = e.progressPercent,
                    onClick = { onOpen(e) },
                )
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
    onNearEnd: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState, row.key) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.distinctUntilChanged().collect { (last, total) ->
            if (total > 0 && last >= total - PAGE_AHEAD) onNearEnd()
        }
    }
    Column {
        RowTitle(row.title)
        if (!row.loaded) {
            PlaceholderRow()
            return@Column
        }
        LazyRow(
            state = listState,
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(row.items, key = { _, it -> it.id }) { index, item ->
                PosterCard(
                    title = item.displayTitle,
                    subtitle = item.displayYear,
                    imageUrl = ImageUrl.resolve(serverUrl, item.screenshotUri),
                    onClick = { onOpen(item) },
                    onFocused = {
                        onFocusItem(item)
                        if (index >= row.items.size - PAGE_AHEAD) onNearEnd()
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaceholderRow() {
    Row(
        Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(7) {
            Box(
                Modifier
                    .width(PosterWidth)
                    .padding(bottom = 48.dp)
                    .background(AppColors.Surface, RoundedCornerShape(10.dp))
                    .then(Modifier.posterAspect()),
            )
        }
    }
}

@Composable
private fun FilterGridContent(
    grid: FilterGrid,
    serverUrl: String?,
    onOpen: (MediaItem) -> Unit,
    onFocusItem: (MediaItem) -> Unit,
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
                LaunchedEffect(gridState) {
                    snapshotFlow {
                        val info = gridState.layoutInfo
                        (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
                    }.distinctUntilChanged().collect { (last, total) ->
                        if (total > 0 && last >= total - PAGE_AHEAD) onNearEnd()
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(PosterWidth + 16.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(grid.items, key = { _, it -> it.id }) { _, item ->
                        PosterCard(
                            title = item.displayTitle,
                            subtitle = item.displayYear,
                            imageUrl = ImageUrl.resolve(serverUrl, item.screenshotUri),
                            onClick = { onOpen(item) },
                            onFocused = { onFocusItem(item) },
                        )
                    }
                }
            }
        }
    }
}
