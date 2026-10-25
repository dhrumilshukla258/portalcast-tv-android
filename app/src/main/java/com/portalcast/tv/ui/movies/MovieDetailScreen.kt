package com.portalcast.tv.ui.movies

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.ui.components.ArtImage
import com.portalcast.tv.ui.components.Backdrop
import com.portalcast.tv.ui.components.ErrorPanel
import com.portalcast.tv.ui.components.ErrorText
import com.portalcast.tv.ui.components.Formatting
import com.portalcast.tv.ui.components.LoadingPanel
import com.portalcast.tv.ui.components.PosterAspect
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.components.TvButtonStyle
import com.portalcast.tv.ui.components.backdropArt
import com.portalcast.tv.ui.components.showWholeWhenFocused
import com.portalcast.tv.ui.components.posterPath
import com.portalcast.tv.ui.components.rememberImageUrl
import com.portalcast.tv.ui.discover.MoreLikeThis
import com.portalcast.tv.ui.discover.MyListButton
import com.portalcast.tv.ui.discover.contentIdOf
import com.portalcast.tv.ui.discover.rememberTitleExtras
import com.portalcast.tv.ui.theme.AppColors

/**
 * Movie detail: backdrop, poster, title, year/rating/genres/runtime, overview, director
 * and cast, then Resume from mm:ss (when there is a resumable progress row) and Play.
 * Back is the NavHost's default pop.
 *
 * @param onPlay called after `PlaybackHolder.start`; navigate to the player.
 */
@Composable
fun MovieDetailScreen(
    id: String,
    category: String,
    onPlay: () -> Unit,
    onOpenPerson: (Int) -> Unit = {},
    onOpenMovie: (MediaItem) -> Unit = {},
    onOpenSeries: (MediaItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val container = LocalContext.current.appContainer
    val vm: MovieDetailViewModel = viewModel(
        key = "movie-$id-$category",
        factory = viewModelFactory {
            initializer { MovieDetailViewModel(id, category, container.vodRepository, container.userRepository) }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    // Re-entry (back from the player): progress has moved on.
    LaunchedEffect(Unit) { if (vm.shownOnce) vm.refreshProgress() else vm.shownOnce = true }

    val item = state.item
    if (item == null) {
        if (state.loading) LoadingPanel(modifier)
        else ErrorPanel(state.error ?: IllegalStateException("Movie not found"), onRetry = vm::load, modifier = modifier, requestFocus = true)
        return
    }

    // One details request for the page (info and people both use it).
    val details = com.portalcast.tv.ui.components.rememberTitleDetails(item, "movie")
    // My List and More Like This (design-system.md, Detail page).
    val extras = rememberTitleExtras(contentIdOf(item, "movie"))
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(state.progressLoaded) {
        if (state.progressLoaded) {
            withFrameNanos { }
            runCatching { primaryFocus.requestFocus() }
        }
    }

    Box(modifier.fillMaxSize()) {
        Backdrop(rememberImageUrl(item.backdropArt), blurred = item.backdropHdPath == null && item.backdropPath == null, seed = item.displayTitle)
        Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 48.dp)) {
            ArtImage(
                url = rememberImageUrl(item.posterPath),
                fallbackText = item.displayTitle,
                modifier = Modifier.width(240.dp).aspectRatio(PosterAspect).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(40.dp))
            com.portalcast.tv.ui.components.MinimalScrolling {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // The title, facts and overview go with the buttons: back up to Play or My List
                // from Cast & crew or More Like This, the page shows them all again instead of
                // stopping at the buttons with the title off the top (owner, 2026-10-23). The block
                // fits the screen: the overview is capped at 6 lines.
                Column(Modifier.showWholeWhenFocused()) {
                MovieInfo(item, details)
                Spacer(Modifier.height(28.dp))
                if (state.progressLoaded) {
                    // Padding gives the focused button's scale + ring room inside the scrolling column,
                    // which clips its children (the ring was cut off at the bottom).
                    Row(Modifier.padding(vertical = 10.dp, horizontal = 6.dp)) {
                        val resume = state.resumeMs
                        if (resume != null) {
                            TvButton(
                                text = Formatting.resumeFrom(resume),
                                onClick = { vm.play(fromStart = false, onReady = onPlay) },
                                style = TvButtonStyle.Primary,
                                enabled = !state.starting,
                                modifier = Modifier.focusRequester(primaryFocus),
                            )
                            Spacer(Modifier.width(16.dp))
                            TvButton(
                                text = "Play from start",
                                onClick = { vm.play(fromStart = true, onReady = onPlay) },
                                enabled = !state.starting,
                            )
                        } else {
                            TvButton(
                                text = if (state.starting) "Starting…" else "Play",
                                onClick = { vm.play(fromStart = true, onReady = onPlay) },
                                style = TvButtonStyle.Primary,
                                enabled = !state.starting,
                                modifier = Modifier.focusRequester(primaryFocus),
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        MyListButton(extras)
                    }
                } else {
                    Text("Loading…", color = AppColors.TextMuted, style = MaterialTheme.typography.bodyLarge)
                }
                }
                state.playError?.let {
                    Spacer(Modifier.height(16.dp))
                    ErrorText(it, textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                }
                // Cast and crew, focusable: each opens the person's page with all their titles.
                com.portalcast.tv.ui.components.PeopleRow(
                    details,
                    onOpenPerson = { p -> p.id?.let(onOpenPerson) },
                )
                MoreLikeThis(extras, onOpenMovie, onOpenSeries, Modifier.padding(top = 18.dp, bottom = 24.dp))
            }
            }
        }
    }
}

@Composable
private fun MovieInfo(item: MediaItem, details: com.portalcast.tv.data.model.TitleDetails?) {
    com.portalcast.tv.ui.components.TitleHeading(item.displayTitle, details?.logo ?: item.logoPath)
    val meta = Formatting.metaLine(item)
    if (meta.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(meta, style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
    }
    com.portalcast.tv.ui.components.DetailFacts(item, details, series = false)
    // As the series page: the overview and credits cut, in one block that takes focus; OK opens
    // all of it (owner, 2026-10-25: "make the movie page like read more"). Short, so Play fits.
    val castLine = (details?.cast?.takeIf { it.isNotEmpty() }?.joinToString(", ") { c -> c.role?.takeIf { r -> r.isNotBlank() }?.let { r -> "${c.name} ($r)" } ?: c.name }
        ?: item.actors ?: item.cast)?.trim()?.takeIf { it.isNotEmpty() && it != "-" && it != "N/A" }
    val lines = listOfNotNull(
        clean(item.director)?.let { "Director: $it" },
        castLine?.let { "Cast: $it" },
        clean(item.country)?.let { "Country: $it" },
    )
    val overview = clean(item.overview)
    if (overview == null && lines.isEmpty()) return
    var showAbout by remember { mutableStateOf(false) }
    if (showAbout) {
        val all = listOfNotNull(overview, *lines.toTypedArray(), details?.studios?.takeIf { it.isNotBlank() }?.let { "Studio: $it" }).joinToString("\n\n")
        com.portalcast.tv.ui.components.ReadMoreDialog(item.displayTitle, all, onDismiss = { showAbout = false })
    }
    androidx.tv.material3.Surface(
        onClick = { showAbout = true },
        modifier = Modifier.padding(top = 12.dp).widthIn(max = 780.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(2.dp, AppColors.Focus), shape = RoundedCornerShape(10.dp)),
        ),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = AppColors.GlassFillFocused,
            contentColor = AppColors.Text,
            focusedContentColor = AppColors.Text,
        ),
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            overview?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = AppColors.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (lines.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                for (line in lines) {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("OK: more about the film", style = MaterialTheme.typography.labelSmall, color = AppColors.TextMuted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

private fun clean(v: String?): String? = v?.trim()?.takeUnless { it.isEmpty() || it == "-" || it == "N/A" }

