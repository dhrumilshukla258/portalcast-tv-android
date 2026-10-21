package com.portalcast.tv.playback

import com.portalcast.tv.data.model.ProgressMeta
import com.portalcast.tv.data.model.ProgressRecord
import com.portalcast.tv.data.repo.UserRepository
import com.portalcast.tv.data.store.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * [ProgressReporter] implementing contract §6.2, shaped like the webui's
 * `useProgressTracking.ts` so Continue Watching lines up across devices:
 *
 * - at most one save every [SAVE_INTERVAL_MS] while playing, plus one on [flush];
 * - skipped when position ≤ 2 s, no duration, or ≥ 90 %;
 * - movies: one row keyed by the movie id; episodes: two rows (episode file id and
 *   series id) with identical `meta`;
 * - [markCompleted] writes `completed = true, progress = duration`;
 * - [resumePositionMs] = saved progress when > 2 s and not completed;
 * - never anything for live TV.
 *
 * [onProgress] is called from the player's 1 s ticker, so it must not suspend; the
 * actual PUT runs on [scope]. Only one save is in flight at a time.
 */
class DefaultProgressReporter(
    private val users: UserRepository,
    private val hasSession: () -> Boolean,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : ProgressReporter {

    constructor(users: UserRepository, sessionStore: SessionStore) : this(
        users = users,
        hasSession = { sessionStore.session.value != null },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val lock = Any()
    private var activeKey: String? = null
    private var lastSaveAt: Long = 0L
    /** Position of the last save: a paused player (same position) is not saved again. */
    private var lastSavedPositionMs: Long = -1L
    @Volatile private var saving = false

    override fun onProgress(session: PlaybackSession, positionMs: Long, durationMs: Long) {
        if (session is PlaybackSession.Live) return
        val now = clock()
        synchronized(lock) {
            val key = sessionKey(session)
            if (key != activeKey) {
                // New title: the 30 s clock starts now (the webui's interval fires 30 s after mount).
                activeKey = key
                lastSaveAt = now
                lastSavedPositionMs = -1L
                return
            }
            if (now - lastSaveAt < SAVE_INTERVAL_MS) return
            if (!isSavable(positionMs, durationMs) || saving) return
            // Paused (or barely moved): nothing new to tell the server.
            if (lastSavedPositionMs >= 0 && kotlin.math.abs(positionMs - lastSavedPositionMs) < MIN_MOVE_MS) {
                lastSaveAt = now
                return
            }
            lastSaveAt = now
            lastSavedPositionMs = positionMs
        }
        saving = true
        scope.launch {
            try {
                save(session, positionMs, durationMs)
            } finally {
                saving = false
            }
        }
    }

    override suspend fun flush(session: PlaybackSession, positionMs: Long, durationMs: Long) {
        if (session is PlaybackSession.Live) return
        synchronized(lock) {
            activeKey = sessionKey(session)
            lastSaveAt = clock()
        }
        if (!isSavable(positionMs, durationMs)) return
        save(session, positionMs, durationMs)
    }

    override suspend fun markCompleted(session: PlaybackSession, durationMs: Long) {
        if (session is PlaybackSession.Live || !hasSession()) return
        val durationSec = durationMs.coerceAtLeast(0L) / 1000.0
        val meta = buildMeta(session, positionMs = durationMs, durationMs = durationMs) ?: return
        for (id in rowIds(session)) {
            users.saveProgress(ProgressRecord(mediaId = id, progress = durationSec, completed = true, meta = meta.toJson()))
        }
    }

    override suspend fun resumePositionMs(session: PlaybackSession): Long? {
        val wanted = resumeRowId(session) ?: return null
        val rows = users.progress().getOrNull() ?: return null
        val row = rows.firstOrNull { it.mediaId == wanted } ?: return null
        if (row.completed || row.progress <= RESUME_MIN_SEC) return null
        return (row.progress * 1000).toLong()
    }

    private suspend fun save(session: PlaybackSession, positionMs: Long, durationMs: Long) {
        if (!hasSession()) return
        val meta = buildMeta(session, positionMs, durationMs) ?: return
        val progressSec = positionMs / 1000.0
        for (id in rowIds(session)) {
            users.saveProgress(ProgressRecord(mediaId = id, progress = progressSec, completed = false, meta = meta.toJson()))
        }
    }

    /**
     * Mirrors the webui's `progressData`: for movies every id is the movie id; for
     * episodes `id`/`playbackFileId`/`itemId` are the episode file id and `mediaId`
     * is the series id. A blank title is never written (it renders as "??" on the web).
     */
    private fun buildMeta(session: PlaybackSession, positionMs: Long, durationMs: Long): ProgressMeta? {
        val currentSec = positionMs.coerceAtLeast(0L) / 1000.0
        val durationSec = durationMs.coerceAtLeast(0L) / 1000.0
        val percent = if (durationMs > 0) ((positionMs.toDouble() / durationMs) * 100).roundToInt().coerceIn(0, 100) else 0
        return when (session) {
            is PlaybackSession.Live -> null
            is PlaybackSession.Movie -> {
                val title = session.item.displayTitle.takeIf { it.isNotBlank() } ?: return null
                ProgressMeta(
                    id = session.item.id,
                    playbackFileId = session.fileId,
                    mediaId = session.item.id,
                    itemId = session.item.id,
                    seasonId = null,
                    categoryId = session.category ?: session.item.categoryId,
                    type = "movie",
                    title = title,
                    currentTime = currentSec,
                    duration = durationSec,
                    progressPercent = percent,
                    timestamp = clock(),
                    name = title,
                    episodeTitle = title,
                    screenshot_uri = session.item.screenshotUri ?: "",
                    is_series = 0,
                    cmd = null,
                    series_number = null,
                    catalogId = session.item.catalogId ?: "movie_${session.item.rawId}",
                )
            }
            is PlaybackSession.Episode -> {
                val title = session.series.displayTitle.takeIf { it.isNotBlank() } ?: return null
                val episodeTitle = session.episode.displayTitle.ifBlank { session.episode.episodeNum?.let { "Episode $it" } ?: "" }
                ProgressMeta(
                    id = session.fileId,
                    playbackFileId = session.fileId,
                    mediaId = session.series.id,
                    itemId = session.fileId,
                    seasonId = session.seasonId,
                    categoryId = session.category ?: session.series.categoryId,
                    type = "series",
                    title = title,
                    currentTime = currentSec,
                    duration = durationSec,
                    progressPercent = percent,
                    timestamp = clock(),
                    name = title,
                    episodeTitle = episodeTitle,
                    screenshot_uri = session.series.screenshotUri ?: session.episode.screenshotUri ?: "",
                    is_series = 1,
                    cmd = null,
                    series_number = session.episode.seriesNumber,
                    catalogId = session.series.catalogId ?: "series_${session.series.rawId}",
                )
            }
        }
    }

    private fun rowIds(session: PlaybackSession): List<String> = when (session) {
        is PlaybackSession.Live -> emptyList()
        is PlaybackSession.Movie -> listOf(session.item.id)
        is PlaybackSession.Episode -> listOf(session.fileId, session.series.id).distinct()
    }

    /** The webui restores from the row keyed by `itemId || mediaId`: the file row for episodes, the movie row for movies. */
    private fun resumeRowId(session: PlaybackSession): String? = when (session) {
        is PlaybackSession.Live -> null
        is PlaybackSession.Movie -> session.item.id
        is PlaybackSession.Episode -> session.fileId
    }

    private fun sessionKey(session: PlaybackSession): String = when (session) {
        is PlaybackSession.Live -> "live:${session.channel.id}"
        is PlaybackSession.Movie -> "movie:${session.item.id}:${session.fileId}"
        is PlaybackSession.Episode -> "episode:${session.fileId}"
    }

    companion object {
        const val SAVE_INTERVAL_MS = 30_000L
        const val MIN_MOVE_MS = 5_000L
        const val COMPLETION_FRACTION = 0.9
        const val RESUME_MIN_SEC = 2.0

        /** Contract §6.2 skip rules: `currentTime <= 2`, no duration, or ≥ 90 %. */
        fun isSavable(positionMs: Long, durationMs: Long): Boolean {
            if (durationMs <= 0L) return false
            if (positionMs <= RESUME_MIN_SEC * 1000) return false
            return positionMs.toDouble() / durationMs < COMPLETION_FRACTION
        }
    }
}
