package com.portalcast.tv.ui.series

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.ui.movies.CatalogScreen
import com.portalcast.tv.ui.movies.CatalogSource
import com.portalcast.tv.ui.movies.CatalogViewModel

/** Series tab: same layout as Movies over the series routes. Center → [onOpenSeries]. */
@Composable
fun SeriesScreen(onOpenSeries: (MediaItem) -> Unit, modifier: Modifier = Modifier, restoreFocus: Boolean = false, library: Boolean = false) {
    val container = LocalContext.current.appContainer
    // library: only the NAS files; otherwise the IPTV provider's categories.
    val vm: CatalogViewModel = viewModel(
        key = if (library) "catalog-series-library" else "catalog-series",
        factory = viewModelFactory { initializer { CatalogViewModel(CatalogSource.Series(container.seriesRepository), if (library) com.portalcast.tv.ui.movies.LOCAL_CATEGORY else null) } },
    )
    // IPTV categories and My library stay as they are: no title menu here (owner).
    androidx.compose.runtime.CompositionLocalProvider(com.portalcast.tv.ui.components.LocalTitleMenu provides null) {
        CatalogScreen(vm = vm, title = if (library) "My library · Series" else "IPTV categories · Series", onOpen = onOpenSeries, modifier = modifier, restoreFocus = restoreFocus)
    }
}
