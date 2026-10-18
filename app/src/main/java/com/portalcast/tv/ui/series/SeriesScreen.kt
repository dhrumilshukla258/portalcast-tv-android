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
fun SeriesScreen(onOpenSeries: (MediaItem) -> Unit, modifier: Modifier = Modifier, restoreFocus: Boolean = false) {
    val container = LocalContext.current.appContainer
    val vm: CatalogViewModel = viewModel(
        key = "catalog-series",
        factory = viewModelFactory { initializer { CatalogViewModel(CatalogSource.Series(container.seriesRepository)) } },
    )
    CatalogScreen(vm = vm, title = "Series", onOpen = onOpenSeries, modifier = modifier, restoreFocus = restoreFocus)
}
