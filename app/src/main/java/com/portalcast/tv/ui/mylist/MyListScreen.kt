package com.portalcast.tv.ui.mylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.repo.DiscoverRepository
import com.portalcast.tv.ui.components.ErrorPanel
import com.portalcast.tv.ui.components.LoadingPanel
import com.portalcast.tv.ui.components.MediaCard
import com.portalcast.tv.ui.components.gridItem
import com.portalcast.tv.ui.components.keepColumn
import com.portalcast.tv.ui.components.rememberGridFocus
import com.portalcast.tv.ui.components.revealWithTitle
import com.portalcast.tv.ui.discover.DiscoverOpener
import com.portalcast.tv.ui.discover.OpenerEffects
import com.portalcast.tv.ui.theme.AppColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyListUiState(
    val loading: Boolean = true,
    val error: Throwable? = null,
    /** Null until the first answer. */
    val items: List<MediaItem>? = null,
)

/** `GET /api/user/list`: the viewer's saved titles, newest first. Reloaded each time the tab shows. */
class MyListViewModel(private val discover: DiscoverRepository) : ViewModel() {
    private val _state = MutableStateFlow(MyListUiState())
    val state: StateFlow<MyListUiState> = _state.asStateFlow()
    val opener = DiscoverOpener(discover, viewModelScope)
    var lastFocusedIndex = 0

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            discover.myList().fold(
                { list -> _state.update { it.copy(loading = false, items = list) } },
                // A failed refresh keeps what is shown.
                { e -> _state.update { it.copy(loading = false, error = e.takeIf { _ -> it.items == null }) } },
            )
        }
    }
}

private const val COLUMNS = 6

/** The My List tab (design-system.md, Screens): a grid of saved titles; Add / Remove is on each detail page. */
@Composable
fun MyListScreen(onOpenMovie: (MediaItem) -> Unit, onOpenSeries: (MediaItem) -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val vm: MyListViewModel = viewModel(key = "my-list", factory = viewModelFactory { initializer { MyListViewModel(container.discoverRepository) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val serverUrl by container.sessionStore.serverUrl.collectAsStateWithLifecycle()
    val opening by vm.opener.state.collectAsStateWithLifecycle()
    OpenerEffects(vm.opener, serverUrl, onOpenMovie, onOpenSeries)
    LaunchedEffect(Unit) { vm.load() }

    val items = state.items
    if (items == null) {
        if (state.loading) LoadingPanel(modifier) else ErrorPanel(state.error ?: IllegalStateException("My List is unavailable"), onRetry = vm::load, modifier = modifier, requestFocus = true)
        return
    }
    Column(modifier.fillMaxSize().padding(start = 48.dp, end = 32.dp, top = 32.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("My List", style = MaterialTheme.typography.headlineSmall, color = AppColors.Text)
            if (items.isNotEmpty()) Text("${items.size} ${if (items.size == 1) "title" else "titles"}", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted)
        }
        opening.openError?.let { Text(it, color = AppColors.Error, modifier = Modifier.padding(top = 8.dp)) }
        if (items.isEmpty()) {
            Text(
                "Nothing here yet. Choose \"+ My List\" on a movie or series to keep it here.",
                color = AppColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 24.dp),
            )
            return@Column
        }
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(items) { repeat(10) { withFrameNanos { }; if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect } }
        val gridState = rememberLazyGridState()
        val gridFocus = rememberGridFocus(gridState)
        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            state = gridState,
            modifier = Modifier.fillMaxSize().keepColumn(gridFocus, items.size),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                MediaCard(
                    item = item,
                    onClick = { vm.lastFocusedIndex = i; vm.opener.open(item) },
                    width = Dp.Unspecified,
                    modifier = (if (i == vm.lastFocusedIndex.coerceAtMost(items.lastIndex)) Modifier.focusRequester(firstFocus) else Modifier)
                        .gridItem(gridFocus, i)
                        .revealWithTitle(),
                )
            }
        }
    }
}
