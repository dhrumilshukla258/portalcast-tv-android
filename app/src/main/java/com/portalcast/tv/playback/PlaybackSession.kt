package com.portalcast.tv.playback

import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.MediaItem

/**
 * What the player is asked to play. Built by the browse screens, handed to the
 * player route through [PlaybackHolder] (not route args: too large, and tokens
 * must not end up in the back stack).
 */
sealed class PlaybackSession {
    /** Live channel plus the list it was chosen from, for channel up/down. */
    data class Live(val channel: Channel, val playlist: List<Channel>) : PlaybackSession()

    /** A movie: the list/detail item (for title + art) and the resolved playable file id. */
    data class Movie(val item: MediaItem, val fileId: String, val category: String?) : PlaybackSession()

    /** An episode, with its siblings for next/previous. */
    data class Episode(
        val series: MediaItem,
        val seasonId: String,
        val episode: MediaItem,
        val fileId: String,
        val siblings: List<MediaItem>,
        val category: String?,
    ) : PlaybackSession() {
        val nextEpisode: MediaItem? get() = siblings.getOrNull(siblings.indexOfFirst { it.id == episode.id } + 1)
        val previousEpisode: MediaItem? get() = siblings.getOrNull(siblings.indexOfFirst { it.id == episode.id } - 1)
    }

    /** The admin "who's watching" label (contract §3.4): real title, never the file's quality string. */
    val label: String
        get() = when (this) {
            is Live -> channel.displayName
            is Movie -> item.displayTitle
            is Episode -> "${series.displayTitle} - ${episode.displayTitle.ifBlank { "Episode ${episode.episodeNum ?: ""}".trim() }}"
        }

    val isLive: Boolean get() = this is Live
}

/** The URL the player should load. */
data class ResolvedStream(
    val url: String,
    val isHls: Boolean,
)

/**
 * Turns a [PlaybackSession] into a URL (contract §3.4, §5): live = `channel.cmd`
 * as-is; movie/episode = movie-link, prefixed with the server origin. Called
 * again on a 401/403 from the stream (token expired or server restarted).
 */
interface StreamResolver {
    suspend fun resolve(session: PlaybackSession, forceRefresh: Boolean = false): Result<ResolvedStream>
}

/**
 * Progress persistence rules (contract §6.2). Implemented in Phase 1c.
 * Never called for [PlaybackSession.Live].
 */
interface ProgressReporter {
    /** Called roughly every second by the player; the reporter decides when to save (30 s cadence). */
    fun onProgress(session: PlaybackSession, positionMs: Long, durationMs: Long)
    /** Pause, back, stop: flush immediately. */
    suspend fun flush(session: PlaybackSession, positionMs: Long, durationMs: Long)
    /** ≥ 90 % or ended. */
    suspend fun markCompleted(session: PlaybackSession, durationMs: Long)
    /** Saved resume position in ms, or null when not resumable. */
    suspend fun resumePositionMs(session: PlaybackSession): Long?
}

/** Process-wide hand-off between browse screens and the player route. */
object PlaybackHolder {
    @Volatile var current: PlaybackSession? = null
    @Volatile var resumeFromMs: Long? = null

    fun start(session: PlaybackSession, resumeFromMs: Long? = null) {
        current = session
        this.resumeFromMs = resumeFromMs
    }
}
