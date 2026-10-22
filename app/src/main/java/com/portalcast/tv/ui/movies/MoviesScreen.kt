package com.portalcast.tv.ui.movies

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.MediaItem

/** Movies tab: categories (All first), search, Latest/A–Z sort, paged poster grid. Center → [onOpenMovie]. */
@Composable
fun MoviesScreen(onOpenMovie: (MediaItem) -> Unit, modifier: Modifier = Modifier, restoreFocus: Boolean = false, library: Boolean = false) {
    val container = LocalContext.current.appContainer
    // library: only the NAS files; otherwise the IPTV provider's categories.
    val vm: CatalogViewModel = viewModel(
        key = if (library) "catalog-movies-library" else "catalog-movies",
        factory = viewModelFactory { initializer { CatalogViewModel(CatalogSource.Movies(container.vodRepository), if (library) LOCAL_CATEGORY else null) } },
    )
    CatalogScreen(vm = vm, title = if (library) "My library · Movies" else "IPTV categories · Movies", onOpen = onOpenMovie, modifier = modifier, restoreFocus = restoreFocus)
}
