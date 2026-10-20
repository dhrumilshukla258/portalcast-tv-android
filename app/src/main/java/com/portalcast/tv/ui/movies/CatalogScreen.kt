package com.portalcast.tv.ui.movies

import com.portalcast.tv.ui.components.rememberServerUrl
import com.portalcast.tv.ui.components.FeedAmbient
import com.portalcast.tv.ui.components.uniqueKeys
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.mutableIntStateOf
import com.portalcast.tv.ui.components.rememberedIn
import com.portalcast.tv.ui.components.rememberFocusMemory
import com.portalcast.tv.ui.components.focusMemory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.ui.components.EmptyPanel
import com.portalcast.tv.ui.components.ErrorPanel
import com.portalcast.tv.ui.components.LoadingPanel
import com.portalcast.tv.ui.components.MediaCard
import com.portalcast.tv.ui.components.SideListItem
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.components.panelMessage
import com.portalcast.tv.ui.theme.AppColors

/**
 * The Movies/Series tab body: category column, search + sort header and a 5-column
 * poster grid that pages 14 at a time as the visible end approaches.
 *
 * @param title "Movies" or "Series", shown above the grid.
 * @param onOpen called with the clicked item (already stamped with a category when it had none).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CatalogScreen(
    vm: CatalogViewModel,
    title: String,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    restoreFocus: Boolean = false,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val groupsError = state.groupsError
    if (groupsError != null) {
        ErrorPanel(groupsError, onRetry = vm::reload, modifier = modifier)
        return
    }
    if (state.groupsLoading && state.groups.isEmpty()) {
        LoadingPanel(modifier)
        return
    }

    val restoreRequester = remember { FocusRequester() }
    val gridRequester = remember { FocusRequester() }
    var gridShown by remember { mutableStateOf(false) }
    // The category list is only shown while it (or nothing yet) has focus: once you are in a
    // category the grid takes the whole width. Left from the grid's first column brings the
    // list back and focuses it.
    val sideRequester = remember { FocusRequester() }
    var sideVisible by remember { mutableStateOf(true) }
    var revealSide by remember { mutableStateOf(false) }
    var focusedColumn by remember { mutableIntStateOf(0) }
    LaunchedEffect(revealSide) {
        if (!revealSide) return@LaunchedEffect
        sideVisible = true
        repeat(10) {
            withFrameNanos { }
            if (runCatching { sideRequester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    LaunchedEffect(restoreFocus) {
        if (restoreFocus && vm.lastFocusedId != null) {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { restoreRequester.requestFocus() }
        }
    }

    val groupsMemory = rememberFocusMemory()
    Row(modifier.fillMaxSize().padding(top = 32.dp)) {
        if (sideVisible) LazyColumn(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .focusRequester(sideRequester)
                .onFocusChanged { if (it.hasFocus) revealSide = false }
                .focusMemory(groupsMemory),
            contentPadding = PaddingValues(start = 24.dp, end = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(state.groups, key = { i, g -> "$i:${g.id}" }) { _, group ->
                SideListItem(
                    text = group.displayTitle,
                    selected = group.id == state.selectedCategory,
                    onClick = { vm.selectCategory(group.id) },
                    modifier = Modifier.rememberedIn(groupsMemory),
                )
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 32.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = AppColors.Text)
                    Text(
                        listOfNotNull(
                            state.selectedGroup?.displayTitle,
                            state.list.totalItems?.takeIf { it > 0 }?.let { "$it titles" },
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextMuted,
                    )
                }
                // No per-category search: search is global (Search tab in the rail).
                TvButton(text = "Sort: ${state.sort.label}", onClick = vm::toggleSort)
            }

            Box(Modifier.fillMaxSize()) {
                val list = state.list
                FeedAmbient(rememberServerUrl(), list.items)
                val pageError = state.pageError
                when {
                    list.isEmpty && pageError != null -> ErrorPanel(pageError, onRetry = vm::loadMore)
                    list.isEmpty && (state.pageLoading || !list.hasLoaded) -> LoadingPanel()
                    list.isEmpty -> EmptyPanel(
                        if (state.query.isNotBlank()) "Nothing matches \"${state.query.trim()}\"." else "Nothing in this category yet.",
                    )
                    else -> {
                        val gridState = rememberLazyGridState()
                        LaunchedEffect(gridState) {
                            snapshotFlow { (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to gridState.layoutInfo.totalItemsCount }
                                .collect { (last, _) -> vm.onVisible(last) }
                        }
                        DisposableEffect(Unit) { gridShown = true; onDispose { gridShown = false } }
                        val keys = remember(list.items) { uniqueKeys(list.items.map { it.id }) }
                        val memory = rememberFocusMemory()
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(GRID_COLUMNS),
                            state = gridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(gridRequester)
                                .onFocusChanged { if (it.hasFocus) sideVisible = false }
                                .onPreviewKeyEvent { ev ->
                                    val wantsSide = ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft && focusedColumn == 0 && !sideVisible
                                    if (wantsSide) revealSide = true
                                    wantsSide
                                }
                                .focusMemory(memory),
                            contentPadding = PaddingValues(start = 16.dp, end = 32.dp, top = 16.dp, bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            itemsIndexed(list.items, key = { i, _ -> keys[i] }) { i, item ->
                                MediaCard(
                                    item = item,
                                    onClick = {
                                        vm.lastFocusedId = item.id
                                        onOpen(item.withCategory(state.selectedCategory))
                                    },
                                    width = Dp.Unspecified,
                                    modifier = Modifier
                                        .onFocusChanged { if (it.hasFocus) { vm.lastFocusedId = item.id; focusedColumn = i % GRID_COLUMNS } }
                                        .then(if (item.id == vm.lastFocusedId) Modifier.focusRequester(restoreRequester) else Modifier)
                                        .rememberedIn(memory),
                                )
                            }
                            if (pageError != null) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(pageError.panelMessage(), color = AppColors.Error, modifier = Modifier.weight(1f))
                                        TvButton(text = "Retry", onClick = vm::loadMore)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val GRID_COLUMNS = 5

/** Stamp the browsing category on an item that has none, so detail/playback get a real one. */
internal fun MediaItem.withCategory(selected: String): MediaItem =
    if (categoryId.isNullOrBlank() && selected.isNotBlank() && selected != "*") copy(categoryId = selected) else this
