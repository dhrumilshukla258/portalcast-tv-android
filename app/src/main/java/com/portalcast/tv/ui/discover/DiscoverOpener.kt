package com.portalcast.tv.ui.discover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.repo.DiscoverRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OpenState(
    /** Several versions of the title: the picker is showing. */
    val variants: List<MediaItem>? = null,
    val resolving: Boolean = false,
    val openError: String? = null,
    /** The title to open and whether it is a series; cleared once handled. */
    val navigation: Pair<MediaItem, Boolean>? = null,
)

/**
 * Opens a Discover card from outside Discover (a person's page, More Like This): offers the
 * versions when there are several, then resolves the chosen one to a playable item.
 */
class DiscoverOpener(private val discover: DiscoverRepository, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(OpenState())
    val state: StateFlow<OpenState> = _state.asStateFlow()

    fun open(item: MediaItem) {
        if (_state.value.resolving) return
        _state.update { it.copy(resolving = true, openError = null) }
        scope.launch {
            val variants = discover.variants(item.id).getOrNull().orEmpty()
            if (variants.size > 1) _state.update { it.copy(resolving = false, variants = variants) } else resolve(item)
        }
    }

    fun chooseVariant(v: MediaItem) {
        _state.update { it.copy(variants = null, resolving = true) }
        scope.launch { resolve(v) }
    }

    fun dismissVariants() = _state.update { it.copy(variants = null) }
    fun onNavigationHandled() = _state.update { it.copy(navigation = null) }

    private suspend fun resolve(item: MediaItem) {
        discover.openItem(item).fold(
            { r -> _state.update { it.copy(resolving = false, navigation = r to ((r.discoverType ?: item.discoverType) == "series")) } },
            { _ -> _state.update { it.copy(resolving = false, openError = "Couldn't open \"${item.displayTitle}\"") } },
        )
    }
}

/** The opener's navigation and version picker, for the screen that owns it. */
@Composable
fun OpenerEffects(opener: DiscoverOpener, serverUrl: String?, onOpenMovie: (MediaItem) -> Unit, onOpenSeries: (MediaItem) -> Unit) {
    val state by opener.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.navigation) {
        val nav = state.navigation ?: return@LaunchedEffect
        opener.onNavigationHandled()
        if (nav.second) onOpenSeries(nav.first) else onOpenMovie(nav.first)
    }
    state.variants?.let { v -> VariantPickerDialog(v, serverUrl, onChoose = opener::chooseVariant, onDismiss = opener::dismissVariants) }
}
