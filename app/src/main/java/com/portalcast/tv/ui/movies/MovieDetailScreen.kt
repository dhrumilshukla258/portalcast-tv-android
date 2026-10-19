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
import com.portalcast.tv.ui.components.posterPath
import com.portalcast.tv.ui.components.rememberImageUrl
import com.portalcast.tv.ui.theme.AppColors

/**
 * Movie detail: backdrop, poster, title, year/rating/genres/runtime, overview, director
 * and cast, then Resume from mm:ss (when there is a resumable progress row) and Play.
 * Back is the NavHost's default pop.
 *
 * @param onPlay called after `PlaybackHolder.start`; navigate to the player.
 */
@Composable
fun MovieDetailScreen(id: String, category: String, onPlay: () -> Unit, modifier: Modifier = Modifier) {
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

    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(state.progressLoaded) {
        if (state.progressLoaded) {
            withFrameNanos { }
            runCatching { primaryFocus.requestFocus() }
        }
    }

    Box(modifier.fillMaxSize()) {
        Backdrop(rememberImageUrl(item.backdropArt), blurred = item.backdropHdPath == null && item.backdropPath == null)
        Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 48.dp)) {
            ArtImage(
                url = rememberImageUrl(item.posterPath),
                fallbackText = item.displayTitle,
                modifier = Modifier.width(240.dp).aspectRatio(PosterAspect).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(40.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                MovieInfo(item)
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
                    }
                } else {
                    Text("Loading…", color = AppColors.TextMuted, style = MaterialTheme.typography.bodyLarge)
                }
                state.playError?.let {
                    Spacer(Modifier.height(16.dp))
                    ErrorText(it, textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                }
            }
        }
    }
}

@Composable
private fun MovieInfo(item: MediaItem) {
    Text(
        item.displayTitle.ifBlank { "Untitled" },
        style = MaterialTheme.typography.displaySmall,
        color = AppColors.Text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    val meta = Formatting.metaLine(item)
    if (meta.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(meta, style = MaterialTheme.typography.titleSmall, color = AppColors.TextMuted)
    }
    item.overview?.let {
        Spacer(Modifier.height(18.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.Text,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 760.dp),
        )
    }
    Credit("Director", item.director)
    Credit("Cast", item.actors ?: item.cast)
    Credit("Country", item.country)
}

@Composable
private fun Credit(label: String, value: String?) {
    val v = value?.trim()?.takeUnless { it.isEmpty() || it == "-" || it == "N/A" } ?: return
    Spacer(Modifier.height(10.dp))
    Text(
        "$label: $v",
        style = MaterialTheme.typography.bodyMedium,
        color = AppColors.TextMuted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 760.dp),
    )
}
