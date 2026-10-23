package com.portalcast.tv.ui.series

import androidx.compose.ui.platform.LocalDensity
import com.portalcast.tv.ui.discover.PadRectHorizontally
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.relocation.bringIntoViewResponder
import com.portalcast.tv.ui.discover.PivotRowToTop
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.key
import com.portalcast.tv.ui.components.peopleEntries
import com.portalcast.tv.ui.components.uniqueKeys
import com.portalcast.tv.ui.components.rememberedIn
import com.portalcast.tv.ui.components.rememberFocusMemory
import com.portalcast.tv.ui.components.focusMemory
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.ui.components.ArtImage
import com.portalcast.tv.ui.components.Backdrop
import com.portalcast.tv.ui.components.EmptyPanel
import com.portalcast.tv.ui.components.ErrorPanel
import com.portalcast.tv.ui.components.ErrorText
import com.portalcast.tv.ui.components.Formatting
import com.portalcast.tv.ui.components.LoadingPanel
import com.portalcast.tv.ui.components.PosterAspect
import com.portalcast.tv.ui.components.ProgressLine
import com.portalcast.tv.ui.components.ProgressRows
import com.portalcast.tv.ui.components.TvButton
import com.portalcast.tv.ui.components.TvButtonStyle
import com.portalcast.tv.ui.components.WideAspect
import com.portalcast.tv.ui.components.backdropArt
import com.portalcast.tv.ui.components.panelMessage
import com.portalcast.tv.ui.components.posterPath
import com.portalcast.tv.ui.components.rememberImageUrl
import com.portalcast.tv.ui.discover.MoreLikeThis
import com.portalcast.tv.ui.discover.MyListButton
import com.portalcast.tv.ui.discover.contentIdOf
import com.portalcast.tv.ui.discover.rememberTitleExtras
import com.portalcast.tv.ui.theme.AppColors

/**
 * Series detail: backdrop, title, overview, "Resume S1E3" (from the series' own progress
 * row), season tabs and the selected season's episodes with per-episode progress.
 * Center on an episode resolves its file and plays it; Back is the NavHost's default pop.
 *
 * @param onPlay called after `PlaybackHolder.start`; navigate to the player.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SeriesDetailScreen(
    id: String,
    category: String,
    onPlay: () -> Unit,
    onOpenPerson: (Int) -> Unit = {},
    onOpenMovie: (MediaItem) -> Unit = {},
    onOpenSeries: (MediaItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val container = LocalContext.current.appContainer
    val vm: SeriesDetailViewModel = viewModel(
        key = "series-$id-$category",
        factory = viewModelFactory {
            initializer { SeriesDetailViewModel(id, category, container.seriesRepository, container.userRepository, container.discoverRepository) }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (vm.shownOnce) vm.refreshProgress() else vm.shownOnce = true }

    val series = state.series
    val continueRow = state.continueRow
    val resumeMs = continueRow?.let { ProgressRows.resumableMs(it) }

    // One initial focus per showing, on the episode the viewer is at: the episode focus was
    // on before the player (Back from playback), else the episode the series' progress row
    // points at (its season is already the selected one), else Resume when that episode is
    // not in the list, else the first episode. While the progress episode's season is still
    // loading nothing is focused yet, so focus does not settle on Resume and then stay there.
    val initialFocus = remember { FocusRequester() }
    var focusDone by remember { mutableStateOf(false) }
    val restoreId = vm.lastFocusedEpisodeId?.takeIf { rid -> state.episodes.items.any { it.id == rid } }
    val continueId = state.continueEpisode?.id?.takeIf { cid -> state.episodes.items.any { it.id == cid } }
    val seasonSettled = state.episodes.endReached || state.episodesError != null || (state.episodes.hasLoaded && !state.episodesLoading)
    val focusTarget: String? = when {
        restoreId != null -> "ep:$restoreId"
        continueId != null -> "ep:$continueId"
        continueRow != null && !seasonSettled && state.seasonsError == null -> null
        continueRow != null -> "resume"
        state.episodes.items.isNotEmpty() -> "ep:${state.episodes.items.first().id}"
        state.seasonsError != null || (state.episodes.hasLoaded && state.episodes.isEmpty) -> "back"
        else -> null
    }
    LaunchedEffect(focusTarget) {
        if (focusTarget == null || focusDone) return@LaunchedEffect
        // The target row may still be scrolling into view (EpisodeList scrolls to it first).
        repeat(12) {
            withFrameNanos { }
            if (runCatching { initialFocus.requestFocus() }.isSuccess) { focusDone = true; return@LaunchedEffect }
        }
    }
    fun Modifier.initialFocusIf(key: String): Modifier = if (key == focusTarget) this.focusRequester(initialFocus) else this

    // One details request for the page (heading, facts and the cast panel all use it).
    val titleDetails = com.portalcast.tv.ui.components.rememberTitleDetails(series, "series")
    Box(modifier.fillMaxSize()) {
        val hasBackdrop = series.backdropHdPath != null || series.backdropPath != null
        Backdrop(rememberImageUrl(series.backdropArt), blurred = !hasBackdrop, seed = series.displayTitle)
        // Netflix layout: the show (title, details, Resume, seasons) on the left, the selected
        // season's episodes as a list on the right. The left column adapts to what the title
        // has: a real backdrop carries the page on its own; without one the poster sits beside
        // the text; an overview, cast, director and country appear only when present.
        val extras = rememberTitleExtras(contentIdOf(series, "series"))
        // The page is one screen high; More Like This sits under it and Down from the seasons or
        // the episodes lifts the page to show it (owner, 2026-10-22: no popup).
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageHeight = maxHeight
        com.portalcast.tv.ui.components.MinimalScrolling {
        val pageScroll = rememberScrollState()
        val pageScope = rememberCoroutineScope()
        // More Like This opens on request (owner, 2026-10-22: on a TV it should not show by itself).
        var showMore by remember { mutableStateOf(false) }
        val moreButton = remember { FocusRequester() }
        val moreFirst = remember { FocusRequester() }
        val extrasState by extras.state.collectAsStateWithLifecycle()
        Column(Modifier.fillMaxSize().verticalScroll(pageScroll)) {
        // Bottom padding: the episode list ends with a gap above More Like This. Focus back in the
        // page (Up from More Like This) scrolls it back to the top, whole.
        Row(
            Modifier
                .fillMaxWidth()
                .height(pageHeight)
                .onFocusChanged {
                    if (!it.hasFocus) return@onFocusChanged
                    showMore = false
                    if (pageScroll.value != 0) pageScope.launch { pageScroll.animateScrollTo(0) }
                }
                .focusGroup()
                .padding(start = 56.dp, end = 40.dp, top = 40.dp, bottom = 28.dp),
        ) {
            // About 40 % of the screen: the episode list gets the rest, since episode names
            // (daily soaps especially) are long. It scrolls: a long overview no longer pushes
            // the seasons below the screen.
            Column(Modifier.width(380.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                val poster = rememberImageUrl(series.posterPath)
                Row(verticalAlignment = Alignment.Top) {
                    if (!hasBackdrop && poster != null) {
                        ArtImage(
                            url = poster,
                            fallbackText = series.displayTitle,
                            modifier = Modifier.width(140.dp).aspectRatio(PosterAspect).clip(RoundedCornerShape(10.dp)),
                        )
                        Spacer(Modifier.width(22.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        com.portalcast.tv.ui.components.TitleHeading(series.displayTitle.ifBlank { "Series" }, titleDetails?.logo ?: series.logoPath, maxLines = 3, large = false)
                        val meta = listOfNotNull(
                            Formatting.metaLine(series).takeIf { it.isNotBlank() },
                            state.seasons.size.takeIf { it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" },
                            state.episodes.totalItems?.takeIf { it > 0 }?.let { if (it == 1) "1 episode" else "$it episodes" },
                        ).joinToString("  ·  ")
                        if (meta.isNotBlank()) {
                            Text(meta, style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted, modifier = Modifier.padding(top = 6.dp))
                        }
                        com.portalcast.tv.ui.components.DetailFacts(series, titleDetails, series = true)
                        series.overview?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.Text,
                                maxLines = if (hasBackdrop) 5 else 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        val details = listOfNotNull(
                            series.director?.takeIf { it.isNotBlank() && it != "-" }?.let { "Created by: $it" },
                            // Characters too when the server has them: "Andy Samberg (Jake Peralta)".
                            (titleDetails?.cast?.takeIf { it.isNotEmpty() }?.take(5)?.joinToString(", ") { c -> c.role?.takeIf { r -> r.isNotBlank() }?.let { r -> "${c.name} ($r)" } ?: c.name }
                                ?: series.cast)?.takeIf { it.isNotBlank() && it != "-" }?.let { "Cast: $it" },
                            series.country?.takeIf { it.isNotBlank() && it != "-" }?.let { "Country: $it" },
                        )
                        if (details.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            for (line in details) {
                                Text(line, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (continueRow != null) {
                    Spacer(Modifier.height(14.dp))
                    TvButton(
                        text = run {
                            // Season from the season list (not episode.seriesNumber: on Stalker
                            // that is the portal's episode selector), episode number parsed.
                            val meta = continueRow.parsedMeta
                            val seasonIdx = state.seasons.indexOfFirst { it.id == meta?.seasonId }
                            val seasonNo = state.seasons.getOrNull(seasonIdx)?.let { EpisodeInfo.seasonNumber(it, seasonIdx) }
                            val ep = state.continueEpisode
                            val epNo = ep?.takeIf { EpisodeInfo.hasOwnNumber(it) }?.let { EpisodeInfo.number(it, 0) }
                            SeriesProgress.resumeLabel(meta, ep, resumeMs, seasonNo, epNo)
                        },
                        onClick = { vm.resumeContinue(onPlay) },
                        style = TvButtonStyle.Primary,
                        enabled = !state.starting,
                        modifier = Modifier.initialFocusIf("resume"),
                    )
                }
                state.playError?.let { ErrorText(it, textAlign = TextAlign.Start, modifier = Modifier.padding(top = 8.dp)) }

                // Cast and crew and My List: the column has no room for a row of people, so a
                // button (cast opens in a dialog). More Like This opens its row under the page.
                val peopleDetails = titleDetails
                var showPeople by remember { mutableStateOf(false) }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (peopleDetails != null && peopleDetails.peopleEntries().any { it.person.id != null }) {
                        TvButton(text = "Cast & crew", onClick = { showPeople = true })
                    }
                    MyListButton(extras)
                    if (extrasState.similar.isNotEmpty()) {
                        TvButton(text = "More Like This", onClick = { showMore = true }, modifier = Modifier.focusRequester(moreButton))
                    }
                }
                if (showPeople && peopleDetails != null) {
                    com.portalcast.tv.ui.components.CastAndCrewDialog(
                        series.displayTitle,
                        peopleDetails,
                        onOpenPerson = { p -> showPeople = false; p.id?.let(onOpenPerson) },
                        onDismiss = { showPeople = false },
                    )
                }

            }

            Spacer(Modifier.width(28.dp))

            val seasonsError = state.seasonsError
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    state.seasonsLoading && state.seasons.isEmpty() -> LoadingPanel(Modifier.fillMaxSize())
                    seasonsError != null -> ErrorPanel(seasonsError, onRetry = vm::loadSeasons, modifier = Modifier.fillMaxSize().initialFocusIf("back"))
                    state.seasons.isEmpty() -> EmptyPanel("No seasons found.", Modifier.fillMaxSize())
                    // Season tabs over the episodes, always on screen (owner, 2026-10-22: in the left
                    // column a long overview pushed the seasons below the screen).
                    else -> Column(Modifier.fillMaxSize()) {
                      SeasonTabs(state, onSelect = vm::selectSeason)
                      EpisodeList(
                        state = state,
                        onPlay = { ep -> vm.playEpisode(ep, onPlay) },
                        onFocused = { vm.lastFocusedEpisodeId = it.id },
                        onVisible = vm::onEpisodeVisible,
                        onRetry = vm::loadMoreEpisodes,
                        episodeModifier = { ep -> Modifier.initialFocusIf("ep:${ep.id}") },
                        scrollToId = focusTarget?.removePrefix("ep:")?.takeIf { focusTarget.startsWith("ep:") },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                      )
                    }
                }
            }
        }
        if (showMore) {
            MoreLikeThis(extras, onOpenMovie, onOpenSeries, Modifier.padding(start = 56.dp, end = 40.dp, top = 8.dp, bottom = 32.dp), firstCard = moreFirst)
            LaunchedEffect(Unit) { repeat(10) { withFrameNanos { }; if (runCatching { moreFirst.requestFocus() }.isSuccess) return@LaunchedEffect } }
            // Back closes the row: focus returns to the button, the page to the top.
            BackHandler { runCatching { moreButton.requestFocus() }; showMore = false }
        }
        }
        }
        }
    }
}

/** Season 1 · Season 2 · …: one row over the episode list; the chosen season is filled. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SeasonTabs(state: SeriesDetailViewModel.State, onSelect: (String) -> Unit) {
    if (state.seasons.size <= 1) return
    val memory = rememberFocusMemory()
    val selectedIdx = state.seasons.indexOfFirst { it.id == state.selectedSeasonId }.coerceAtLeast(0)
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIdx - 2).coerceAtLeast(0))
    // A soft fade where the row continues (it was cut off flat), and the focused tab is kept
    // well clear of the edge (owner, 2026-10-22: Season 5 sat against the edge).
    val fadePx = with(LocalDensity.current) { 56.dp.toPx() }
    val tabPad = remember(fadePx) { PadRectHorizontally(fadePx + 8f) }
    LazyRow(
        state = rowState,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (rowState.canScrollForward) {
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = size.width - fadePx, endX = size.width), blendMode = BlendMode.DstOut)
                }
                if (rowState.canScrollBackward) {
                    drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = 0f, endX = fadePx), blendMode = BlendMode.DstOut)
                }
            }
            .focusMemory(memory),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(state.seasons, key = { i, se -> "$i:${se.id}" }) { i, season ->
            val selected = season.id == state.selectedSeasonId
            TvButton(
                text = EpisodeInfo.seasonLabel(season, i),
                onClick = { onSelect(season.id) },
                style = if (selected) TvButtonStyle.Primary else TvButtonStyle.Secondary,
                modifier = Modifier.rememberedIn(memory).bringIntoViewResponder(tabPad),
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun EpisodeList(
    state: SeriesDetailViewModel.State,
    onPlay: (MediaItem) -> Unit,
    onFocused: (MediaItem) -> Unit,
    onVisible: (Int) -> Unit,
    onRetry: () -> Unit,
    episodeModifier: (MediaItem) -> Modifier,
    /** Episode to bring into view before focus lands on it (the viewer's current episode). */
    scrollToId: String?,
    modifier: Modifier = Modifier,
) {
    val episodes = state.episodes
    val error = state.episodesError
    when {
        episodes.isEmpty && error != null -> { ErrorPanel(error, onRetry = onRetry, modifier = modifier); return }
        episodes.isEmpty && (state.episodesLoading || !episodes.hasLoaded) -> { LoadingPanel(modifier); return }
        episodes.isEmpty -> { EmptyPanel("No episodes found for this season.", modifier); return }
    }
    val listState = rememberLazyListState()
    var scrolledTo by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scrollToId, episodes.items.size) {
        val id = scrollToId ?: return@LaunchedEffect
        if (scrolledTo == id) return@LaunchedEffect
        val idx = episodes.items.indexOfFirst { it.id == id }
        if (idx < 0) return@LaunchedEffect
        // The episode being resumed is the top row, focused (Netflix does the same).
        listState.scrollToItem(idx)
        scrolledTo = id
    }
    LaunchedEffect(listState, state.selectedSeasonId) {
        snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to listState.layoutInfo.totalItemsCount }
            .collect { (last, _) -> onVisible(last) }
    }
    // Netflix-style: a vertical list. Each row is thumbnail left, number + title, runtime
    // and description right; the focused row is highlighted and shows a longer description.
    val keys = remember(episodes.items) { uniqueKeys(episodes.items.map { it.id }) }
    val memory = rememberFocusMemory()
    // The focused episode moves to the top of the list, its whole description under it (owner,
    // 2026-10-22).
    val pivot = remember(listState) { PivotRowToTop { listState.layoutInfo.viewportSize.height } }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().focusMemory(memory),
        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp, start = 8.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(episodes.items, key = { i, _ -> keys[i] }) { i, ep ->
            EpisodeRow(
                episode = ep,
                index = i,
                seriesTitle = state.series.displayTitle,
                isResume = ep.id == state.continueEpisode?.id,
                progress = state.fractions[ep.id],
                onClick = { onPlay(ep) },
                enabled = !state.starting,
                modifier = episodeModifier(ep).rememberedIn(memory).bringIntoViewResponder(pivot).onFocusChanged { if (it.hasFocus) onFocused(ep) },
            )
        }
        if (error != null) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error.panelMessage(), color = AppColors.Error, style = MaterialTheme.typography.bodySmall)
                    TvButton(text = "Retry", onClick = onRetry)
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: MediaItem,
    index: Int,
    seriesTitle: String,
    isResume: Boolean,
    progress: Float?,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val number = EpisodeInfo.number(episode, index)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.Transparent), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, AppColors.Focus), shape = shape),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = AppColors.Text,
            focusedContainerColor = AppColors.GlassFillFocused,
            focusedContentColor = AppColors.Text,
            pressedContainerColor = AppColors.GlassFillFocused,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = AppColors.TextMuted,
        ),
    ) {
        // Netflix proportions: a 130 dp still (about an eighth of the screen width), the
        // title with the runtime beside it and a two-line description. An episode the portal
        // has no still or description for is a single text line, not an empty box.
        val art = rememberImageUrl(episode.posterPath)
        val overview = episode.overview?.trim().orEmpty()
        val compact = art == null && overview.isEmpty()
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = if (compact) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.titleMedium,
                color = if (focused) AppColors.Text else AppColors.TextMuted,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center,
            )
            if (art != null) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.width(130.dp).aspectRatio(WideAspect).clip(RoundedCornerShape(6.dp))) {
                    ArtImage(url = art, fallbackText = "$number", modifier = Modifier.fillMaxSize())
                    if (progress != null && progress > 0f) {
                        ProgressLine(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(5.dp))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    EpisodeInfo.title(episode, index, seriesTitle),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (focused) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(
                    if (isResume) "▶ Continue watching" else null,
                    EpisodeInfo.airDate(episode, seriesTitle),
                    Formatting.runtime(episode),
                    if (progress != null && progress >= 0.9f) "Watched" else null,
                ).joinToString("  ·  ")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted, maxLines = 1, modifier = Modifier.padding(top = 1.dp))
                }
                if (overview.isNotEmpty()) {
                    Text(
                        overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextMuted,
                        // The focused episode shows its whole description (owner, 2026-10-22).
                        maxLines = if (focused) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}
