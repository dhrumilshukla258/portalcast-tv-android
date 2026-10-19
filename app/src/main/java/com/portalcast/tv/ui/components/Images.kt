package com.portalcast.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.portalcast.tv.appContainer
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.util.ImageUrl

/** The signed-in server origin (`sessionStore.serverUrl`), observed so images follow a server change. */
@Composable
fun rememberServerUrl(): String? {
    val container = LocalContext.current.appContainer
    val url by container.sessionStore.serverUrl.collectAsStateWithLifecycle()
    return url
}

/** [path] resolved through [ImageUrl.resolve] against the current server; null when there is nothing to show. */
@Composable
fun rememberImageUrl(path: String?): String? {
    val server = rememberServerUrl()
    return remember(server, path) { ImageUrl.resolve(server, path) }
}

/** Poster art for an item. Only `screenshot_uri`: `cover`/`stream_icon` are raw upstream URLs (contract §7). */
val MediaItem.posterPath: String? get() = screenshotUri

/** Wide art for detail backdrops: TMDB backdrop when enriched, else the poster. */
val MediaItem.backdropArt: String?
    get() = backdropPath?.takeIf { it.isNotBlank() } ?: backdropHdPath?.takeIf { it.isNotBlank() } ?: screenshotUri // regular before HD: cheaper to decode on TV hardware
