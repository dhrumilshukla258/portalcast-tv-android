package com.portalcast.tv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.ui.theme.AppColors
import com.portalcast.tv.util.ImageUrl
import kotlinx.coroutines.delay

/**
 * The webui's AmbientBackdrop on the TV: one shared pool of TMDB backdrops, fed by whatever
 * the viewer browses (Discover rows, Movies/Series grids), rotating behind Movies, Series,
 * Live and Search. The pool is app-wide, so a screen with no art of its own (Live) still
 * shows the last titles seen instead of a flat page.
 */
private const val TMDB_ORIGINAL = "image.tmdb.org/t/p/original/"
private const val TMDB_W1280 = "image.tmdb.org/t/p/w1280/"
// The same through the server's image cache (servers from 2026-10-21).
private const val CACHED_ORIGINAL = "/api/images/tmdb/original/"
private const val CACHED_W1280 = "/api/images/tmdb/w1280/"

object AmbientPool {
    private const val CAP = 80
    internal val urls = mutableStateListOf<String>()

    /** Adds the items' backdrops (HD first; posters are the wrong shape and are skipped). */
    fun offer(serverUrl: String?, items: List<MediaItem>) {
        for (item in items) {
            val path = item.backdropHdPath?.takeIf { it.isNotBlank() } ?: item.backdropPath?.takeIf { it.isNotBlank() } ?: continue
            // The background is drawn at 1280x720, blurred and dimmed: TMDB's 1280 px size is
            // enough and is a fraction of the original's download (often 4K, several MB).
            val url = (ImageUrl.resolve(serverUrl, path) ?: continue).replace(TMDB_ORIGINAL, TMDB_W1280).replace(CACHED_ORIGINAL, CACHED_W1280)
            if (url in urls) continue
            if (urls.size >= CAP) urls.removeAt(0)
            urls.add(url)
        }
    }
}

/** Feeds [items] into the pool whenever the list changes. */
@Composable
fun FeedAmbient(serverUrl: String?, items: List<MediaItem>) {
    LaunchedEffect(serverUrl, items) { AmbientPool.offer(serverUrl, items) }
}

private const val ROTATE_MS = 30_000L
private const val FADE_MS = 1_500
private const val DECODE_W = 1280
private const val DECODE_H = 720

/**
 * Full-screen, dimmed backdrop that crossfades to the next pool image every 30 s. The
 * image and its gradient are drawn into one cached layer, so between fades the GPU only
 * blits it (the TV's Mali-G52 cannot afford to re-shade full-screen layers per frame).
 */
@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pool = AmbientPool.urls
    var current by remember { mutableStateOf(pool.randomOrNull()) }
    LaunchedEffect(Unit) {
        while (true) {
            if (current == null) current = pool.randomOrNull()
            delay(ROTATE_MS)
            if (pool.size > 1) {
                val i = pool.indexOf(current)
                current = pool[(i + 1).mod(pool.size)]
            }
        }
    }
    // First images arriving while nothing is shown yet.
    LaunchedEffect(pool.size) { if (current == null) current = pool.randomOrNull() }

    Box(modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        Crossfade(targetState = current, animationSpec = tween(FADE_MS), label = "ambient") { url ->
            if (url != null) {
                val request = remember(url) {
                    ImageRequest.Builder(context).data(url).size(DECODE_W, DECODE_H).build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.6f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // The webui's from-5 % via-35 % to-80 % darkening, plus a left fade for the rail and lists.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to AppColors.Background.copy(alpha = 0.15f),
                    0.5f to AppColors.Background.copy(alpha = 0.55f),
                    1f to AppColors.Background.copy(alpha = 0.9f),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to AppColors.Background.copy(alpha = 0.75f),
                    0.45f to AppColors.Background.copy(alpha = 0.2f),
                    1f to AppColors.Background.copy(alpha = 0f),
                ),
            ),
        )
    }
}
