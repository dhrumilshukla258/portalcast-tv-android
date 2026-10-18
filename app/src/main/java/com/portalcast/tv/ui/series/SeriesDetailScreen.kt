package com.portalcast.tv.ui.series

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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.focus.focusRestorer
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

    // One initial focus per showing: the last played episode, else Resume, else the first episode.
    val initialFocus = remember { FocusRequester() }
    var focusDone by remember { mutableStateOf(false) }
    val restoreId = vm.lastFocusedEpisodeId?.takeIf { rid -> state.episodes.items.any { it.id == rid } }
    val focusTarget: String? = when {
        restoreId != null -> "ep:$restoreId"
        continueRow != null -> "resume"
        state.episodes.items.isNotEmpty() -> "ep:${state.episodes.items.first().id}"
        state.seasonsError != null || (state.episodes.hasLoaded && state.episodes.isEmpty) -> "back"
        else -> null
    }
    LaunchedEffect(focusTarget) {
        if (focusTarget != null && !focusDone) {
            withFrameNanos { }
            withFrameNanos { }
            if (runCatching { initialFocus.requestFocus() }.isSuccess) focusDone = true
        }
    }
    fun Modifier.initialFocusIf(key: String): Modifier = if (key == focusTarget) this.focusRequester(initialFocus) else this

    Box(modifier.fillMaxSize()) {
        Backdrop(rememberImageUrl(series.backdropArt), blurred = series.backdropHdPath == null && series.backdropPath == null)
        Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 40.dp)) {
            Row {
                ArtImage(
                    url = rememberImageUrl(series.posterPath),
                    fallbackText = series.displayTitle,
                    modifier = Modifier.width(132.dp).aspectRatio(PosterAspect).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        series.displayTitle.ifBlank { "Series" },
                        style = MaterialTheme.typography.headlineLarge,
                        color = AppColors.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(
                        Formatting.metaLine(series).takeIf { it.isNotBlank() },
                        state.seasons.size.takeIf { it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" },
                    ).joinToString("  ·  ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted, modifier = Modifier.padding(top = 6.dp))
                    }
                    series.overview?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.Text,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 10.dp).widthIn(max = 820.dp),
                        )
                    }
                    if (continueRow != null) {
                        Spacer(Modifier.height(14.dp))
                        TvButton(
                            text = SeriesProgress.resumeLabel(continueRow.parsedMeta, state.continueEpisode, resumeMs),
                            onClick = { vm.resumeContinue(onPlay) },
                            style = TvButtonStyle.Primary,
                            enabled = !state.starting,
                            modifier = Modifier.initialFocusIf("resume"),
                        )
                    }
                    state.playError?.let { ErrorText(it, textAlign = TextAlign.Start, modifier = Modifier.padding(top = 8.dp)) }
                }
            }

            Spacer(Modifier.height(18.dp))
            val seasonsError = state.seasonsError
            when {
                state.seasonsLoading && state.seasons.isEmpty() -> LoadingPanel(Modifier.weight(1f))
                seasonsError != null -> ErrorPanel(seasonsError, onRetry = vm::loadSeasons, modifier = Modifier.weight(1f).initialFocusIf("back"))
                state.seasons.isEmpty() -> EmptyPanel("No seasons found.", Modifier.weight(1f))
                else -> {
                    LazyRow(
                        modifier = Modifier.focusRestorer(),
                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(state.seasons, key = { i, s -> "$i:${s.id}" }) { i, season ->
                            val selected = season.id == state.selectedSeasonId
                            TvButton(
                                text = season.displayTitle.ifBlank { "Season ${SeriesProgress.seasonNumber(season, i)}" },
                                onClick = { vm.selectSeason(season.id) },
                                style = if (selected) TvButtonStyle.Primary else TvButtonStyle.Secondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    EpisodeList(
                        state = state,
                        onPlay = { ep -> vm.playEpisode(ep, onPlay) },
                        onFocused = { vm.lastFocusedEpisodeId = it.id },
                        onVisible = vm::onEpisodeVisible,
                        onRetry = vm::loadMoreEpisodes,
                        episodeModifier = { ep -> Modifier.initialFocusIf("ep:${ep.id}") },
                        modifier = Modifier.weight(1f),
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
    LaunchedEffect(listState, state.selectedSeasonId) {
        snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to listState.layoutInfo.totalItemsCount }
            .collect { (last, _) -> onVisible(last) }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().focusRestorer(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 40.dp, start = 4.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(episodes.items, key = { i, ep -> "$i:${ep.id}" }) { i, ep ->
            EpisodeRow(
                episode = ep,
                index = i,
                progress = state.fractions[ep.id],
                onClick = { onPlay(ep) },
                enabled = !state.starting,
                modifier = episodeModifier(ep).onFocusChanged { if (it.hasFocus) onFocused(ep) },
            )
        }
        if (error != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(error.panelMessage(), color = AppColors.Error, modifier = Modifier.weight(1f))
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
    progress: Float?,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, AppColors.Focus), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.Surface.copy(alpha = 0.7f),
            contentColor = AppColors.Text,
            focusedContainerColor = AppColors.SurfaceVariant,
            focusedContentColor = AppColors.Text,
            pressedContainerColor = AppColors.SurfaceVariant,
            disabledContainerColor = AppColors.Surface.copy(alpha = 0.5f),
            disabledContentColor = AppColors.TextMuted,
        ),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(176.dp).aspectRatio(WideAspect).clip(RoundedCornerShape(8.dp))) {
                ArtImage(url = rememberImageUrl(episode.posterPath), fallbackText = episode.displayTitle, modifier = Modifier.fillMaxSize())
                if (progress != null && progress > 0f) {
                    ProgressLine(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                val number = episode.episodeNum ?: (index + 1)
                Text(
                    listOfNotNull("$number.", episode.displayTitle.ifBlank { "Episode $number" }).joinToString(" "),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(
                    Formatting.runtime(episode),
                    if (progress != null && progress >= 0.9f) "Watched" else null,
                ).joinToString("  ·  ")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted, modifier = Modifier.padding(top = 2.dp))
                }
                episode.overview?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
