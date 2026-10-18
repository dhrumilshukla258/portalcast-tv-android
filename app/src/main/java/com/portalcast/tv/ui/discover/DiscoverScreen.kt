package com.portalcast.tv.ui.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.ui.nav.PlaceholderScreen

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
    PlaceholderScreen("Discover", "Phase 2b")
}
