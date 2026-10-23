package com.portalcast.tv.ui.discover

import com.portalcast.tv.ui.components.showWholeWhenFocused
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.repo.DiscoverRepository
import com.portalcast.tv.ui.components.PosterRow
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.theme.AppColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TitleExtrasState(
    /** Null until known (or when the server has no My List): the button is hidden. */
    val inList: Boolean? = null,
    val saving: Boolean = false,
    val listError: String? = null,
    val similar: List<MediaItem> = emptyList(),
)

/**
 * A detail page's extras (design-system.md, Detail page): the My List button and the More Like
 * This row, for the title `contentId` ("movie_123" / "series_45").
 */
class TitleExtrasViewModel(private val contentId: String, private val discover: DiscoverRepository) : ViewModel() {
    private val _state = MutableStateFlow(TitleExtrasState())
    val state: StateFlow<TitleExtrasState> = _state.asStateFlow()
    val opener = DiscoverOpener(discover, viewModelScope)

    init {
        viewModelScope.launch {
            discover.myListIds().onSuccess { ids -> _state.update { it.copy(inList = contentId in ids) } }
        }
        viewModelScope.launch {
            discover.similar(contentId).onSuccess { list -> _state.update { it.copy(similar = list.filter { x -> x.id != contentId }) } }
        }
    }

    /** Flips the button at once; puts it back if the server says no. */
    fun toggleList() {
        val now = _state.value
        val was = now.inList ?: return
        if (now.saving) return
        _state.update { it.copy(inList = !was, saving = true, listError = null) }
        viewModelScope.launch {
            val r = discover.setInList(contentId, !was)
            _state.update {
                if (r.isSuccess) it.copy(saving = false)
                else it.copy(inList = was, saving = false, listError = "Couldn't update My List")
            }
        }
    }
}

/** The extras for one title, kept while its detail page is on the back stack. */
@Composable
fun rememberTitleExtras(contentId: String): TitleExtrasViewModel {
    val container = LocalContext.current.appContainer
    return viewModel(key = "extras-$contentId", factory = viewModelFactory { initializer { TitleExtrasViewModel(contentId, container.discoverRepository) } })
}

/** "+ My List" / "✓ In My List" next to Play; nothing when the server has no My List. */
@Composable
fun MyListButton(vm: TitleExtrasViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val inList = state.inList ?: return
    // Never disabled while saving: a disabled button drops focus on a TV.
    TvButton(text = if (inList) "✓ In My List" else "+ My List", onClick = vm::toggleList, modifier = modifier)
}

/** The More Like This row; nothing when there are no similar titles. */
@Composable
fun MoreLikeThis(vm: TitleExtrasViewModel, onOpenMovie: (MediaItem) -> Unit, onOpenSeries: (MediaItem) -> Unit, modifier: Modifier = Modifier, compact: Boolean = true) {
    val container = LocalContext.current.appContainer
    val serverUrl by container.sessionStore.serverUrl.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    OpenerEffects(vm.opener, serverUrl, onOpenMovie, onOpenSeries)
    if (state.similar.isEmpty()) return
    // Home's poster size; the whole row comes on screen when a card in it is focused.
    PosterRow(
        title = "More Like This",
        items = state.similar,
        onItemClick = vm.opener::open,
        modifier = Modifier.showWholeWhenFocused().then(modifier),
        compact = compact,
        cardWidth = 118.dp,
    )
}

/** The catalog id of a movie or series shown on a detail page. */
fun contentIdOf(item: MediaItem, kind: String): String = item.catalogId ?: "${kind}_${item.rawId}"
