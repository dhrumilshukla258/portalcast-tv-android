package com.portalcast.tv.ui.person

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import com.portalcast.tv.ui.components.TvButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
import com.portalcast.tv.data.model.PersonPage
import com.portalcast.tv.data.repo.DiscoverRepository
import com.portalcast.tv.ui.components.ErrorPanel
import com.portalcast.tv.ui.components.LoadingPanel
import com.portalcast.tv.ui.components.MediaCard
import com.portalcast.tv.ui.components.PersonPhoto
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

data class PersonUiState(
    val loading: Boolean = true,
    val error: Throwable? = null,
    val page: PersonPage? = null,
)

/** `GET /api/v2/people/{id}`; opening a title works like a Discover card (version picker, then the detail page). */
class PersonViewModel(private val id: Int, private val discover: DiscoverRepository) : ViewModel() {
    private val _state = MutableStateFlow(PersonUiState())
    val state: StateFlow<PersonUiState> = _state.asStateFlow()
    var lastFocusedIndex = 0
    val opener = DiscoverOpener(discover, viewModelScope)

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            discover.person(id).fold(
                { page -> _state.update { it.copy(loading = false, page = page) } },
                { e -> _state.update { it.copy(loading = false, error = e) } },
            )
        }
    }

}

private const val COLUMNS = 4

/**
 * A person's page: photo, name, what they are known for, birth date and place, biography, and
 * a grid of every title of theirs in the catalog (newest first), each with their role in it.
 */
@Composable
fun PersonScreen(id: Int, onOpenMovie: (MediaItem) -> Unit, onOpenSeries: (MediaItem) -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val vm: PersonViewModel = viewModel(key = "person-$id", factory = viewModelFactory { initializer { PersonViewModel(id, container.discoverRepository) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val serverUrl by container.sessionStore.serverUrl.collectAsStateWithLifecycle()
    val opening by vm.opener.state.collectAsStateWithLifecycle()
    OpenerEffects(vm.opener, serverUrl, onOpenMovie, onOpenSeries)

    val page = state.page
    if (page == null) {
        if (state.loading) LoadingPanel(modifier) else ErrorPanel(state.error ?: IllegalStateException("Person not found"), onRetry = vm::load, modifier = modifier, requestFocus = true)
        return
    }
    val p = page.person
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(page) { repeat(10) { withFrameNanos { }; if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect } }

    Row(modifier.fillMaxSize().padding(start = 56.dp, end = 32.dp, top = 40.dp)) {
        Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
            PersonPhoto(p.name, p.photo, 160)
            Spacer(Modifier.height(16.dp))
            Text(p.name, style = MaterialTheme.typography.headlineMedium, color = AppColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val facts = listOfNotNull(
                p.knownFor?.let { "Known for $it" },
                p.birthday?.let { b -> "Born $b" + (p.placeOfBirth?.let { ", $it" } ?: "") },
                p.deathday?.let { "Died $it" },
                "${page.credits.size} ${if (page.credits.size == 1) "title" else "titles"} here",
            )
            for (f in facts) Text(f, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextMuted, modifier = Modifier.padding(top = 4.dp))
            p.biography?.let { bio ->
                Spacer(Modifier.height(14.dp))
                // 8 lines here; when it is longer, "Read more" opens all of it (owner, 2026-10-22:
                // the end of long biographies was hidden and could not be scrolled to).
                var cut by remember(bio) { mutableStateOf(false) }
                var showBio by remember { mutableStateOf(false) }
                Text(
                    bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Text,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { cut = it.hasVisualOverflow },
                )
                if (cut) {
                    Spacer(Modifier.height(10.dp))
                    TvButton(text = "Read more", onClick = { showBio = true })
                }
                if (showBio) BiographyDialog(p.name, bio, onDismiss = { showBio = false })
            }
            opening.openError?.let { Text(it, color = AppColors.Error, modifier = Modifier.padding(top = 12.dp)) }
        }
        Spacer(Modifier.width(32.dp))
        if (page.credits.isEmpty()) {
            Text("Nothing of theirs in the catalog yet.", color = AppColors.TextMuted, style = MaterialTheme.typography.bodyLarge)
        } else {
            val gridState = rememberLazyGridState()
            val gridFocus = rememberGridFocus(gridState)
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                state = gridState,
                modifier = Modifier.weight(1f).fillMaxHeight().keepColumn(gridFocus, page.credits.size),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                itemsIndexed(page.credits, key = { _, it -> it.id }) { i, item ->
                    val role = item.roles.joinToString(" · ") { r -> r.role?.takeIf { it.isNotBlank() } ?: r.kind.replaceFirstChar { c -> c.uppercase() } }
                    MediaCard(
                        item = item,
                        onClick = { vm.lastFocusedIndex = i; vm.opener.open(item) },
                        subtitle = listOfNotNull(item.displayYear, role.takeIf { it.isNotBlank() }).joinToString(" · "),
                        width = Dp.Unspecified,
                        modifier = (if (i == vm.lastFocusedIndex) Modifier.focusRequester(firstFocus) else Modifier)
                            .gridItem(gridFocus, i)
                            .revealWithTitle(),
                    )
                }
            }
        }
    }

}

/** The whole biography; Up / Down scroll it, Back closes. */
@Composable
private fun BiographyDialog(name: String, text: String, onDismiss: () -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(10) { withFrameNanos { }; if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .widthIn(max = 900.dp)
                .fillMaxHeight(0.85f)
                .background(AppColors.Surface, RoundedCornerShape(16.dp))
                .padding(32.dp),
        ) {
            Text(name, style = MaterialTheme.typography.headlineSmall, color = AppColors.Text)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(260f) }; true }
                            Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-260f) }; true }
                            else -> false
                        }
                    }
                    .verticalScroll(scroll),
            ) {
                Text(text, style = MaterialTheme.typography.bodyLarge, color = AppColors.Text)
            }
            Spacer(Modifier.height(12.dp))
            Text("Up / Down to scroll · Back to close", style = MaterialTheme.typography.labelMedium, color = AppColors.TextMuted)
        }
    }
}
