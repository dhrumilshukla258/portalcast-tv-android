package com.portalcast.tv.ui.series

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
fun SeriesDetailScreen(id: String, category: String, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val vm: SeriesDetailViewModel = viewModel(
        key = "series-$id-$category",
        factory = viewModelFactory {
            initializer { SeriesDetailViewModel(id, category, container.seriesRepository, container.userRepository) }
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

    Box(modifier.fillMaxSize()) {
        val hasBackdrop = series.backdropHdPath != null || series.backdropPath != null
        Backdrop(rememberImageUrl(series.backdropArt), blurred = !hasBackdrop, seed = series.displayTitle)
        // Netflix layout: the show (title, details, Resume, seasons) on the left, the selected
        // season's episodes as a list on the right. The left column adapts to what the title
        // has: a real backdrop carries the page on its own; without one the poster sits beside
        // the text; an overview, cast, director and country appear only when present.
        Row(Modifier.fillMaxSize().padding(start = 56.dp, end = 40.dp, top = 40.dp)) {
            // About 40 % of the screen: the episode list gets the rest, since episode names
            // (daily soaps especially) are long.
            Column(Modifier.width(380.dp).fillMaxHeight()) {
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
                        Text(
                            series.displayTitle.ifBlank { "Series" },
                            style = MaterialTheme.typography.headlineLarge,
                            color = AppColors.Text,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val meta = listOfNotNull(
                            Formatting.metaLine(series).takeIf { it.isNotBlank() },
                            state.seasons.size.takeIf { it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" },
                            state.episodes.totalItems?.takeIf { it > 0 }?.let { if (it == 1) "1 episode" else "$it episodes" },
                        ).joinToString("  ·  ")
                        if (meta.isNotBlank()) {
                            Text(meta, style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted, modifier = Modifier.padding(top = 6.dp))
                        }
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
                            series.director?.takeIf { it.isNotBlank() && it != "-" }?.let { "Director: $it" },
                            series.cast?.takeIf { it.isNotBlank() && it != "-" }?.let { "Cast: $it" },
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

                if (state.seasons.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    Text("Seasons", style = MaterialTheme.typography.labelLarge, color = AppColors.TextMuted, modifier = Modifier.padding(bottom = 8.dp))
                    val seasonsMemory = rememberFocusMemory()
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false).focusMemory(seasonsMemory),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(state.seasons, key = { i, se -> "$i:${se.id}" }) { i, season ->
                            val selected = season.id == state.selectedSeasonId
                            TvButton(
                                text = EpisodeInfo.seasonLabel(season, i),
                                onClick = { vm.selectSeason(season.id) },
                                style = if (selected) TvButtonStyle.Primary else TvButtonStyle.Subtle,
                                modifier = Modifier.width(260.dp).rememberedIn(seasonsMemory),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(28.dp))

            val seasonsError = state.seasonsError
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    state.seasonsLoading && state.seasons.isEmpty() -> LoadingPanel(Modifier.fillMaxSize())
                    seasonsError != null -> ErrorPanel(seasonsError, onRetry = vm::loadSeasons, modifier = Modifier.fillMaxSize().initialFocusIf("back"))
                    state.seasons.isEmpty() -> EmptyPanel("No seasons found.", Modifier.fillMaxSize())
                    else -> EpisodeList(
                        state = state,
                        onPlay = { ep -> vm.playEpisode(ep, onPlay) },
                        onFocused = { vm.lastFocusedEpisodeId = it.id },
                        onVisible = vm::onEpisodeVisible,
                        onRetry = vm::loadMoreEpisodes,
                        episodeModifier = { ep -> Modifier.initialFocusIf("ep:${ep.id}") },
                        scrollToId = focusTarget?.removePrefix("ep:")?.takeIf { focusTarget.startsWith("ep:") },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
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
                modifier = episodeModifier(ep).rememberedIn(memory).onFocusChanged { if (it.hasFocus) onFocused(ep) },
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
                    EpisodeInfo.airDate(episode),
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
                        maxLines = if (focused) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}
