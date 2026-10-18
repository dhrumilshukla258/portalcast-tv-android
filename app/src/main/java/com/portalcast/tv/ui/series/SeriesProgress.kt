package com.portalcast.tv.ui.series

import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.ProgressMeta
import com.portalcast.tv.data.model.ProgressRecord
import com.portalcast.tv.ui.components.Formatting
import com.portalcast.tv.ui.components.ProgressRows

/**
 * Progress-row logic for the series detail screen, matching the webui's
 * SeriesOverviewPage: episodes look up rows by their own id (the reporter's episode
 * row is keyed by the episode file id, which is the episode id on most portals), and
 * the series-id row is the "continue" entry. Pure, JVM-tested.
 */
object SeriesProgress {

    /** Watched fraction per episode/file id: keyed by `mediaId` and by `meta.id`/`meta.itemId`. */
    fun episodeFractions(rows: List<ProgressRecord>, seriesId: String): Map<String, Float> {
        val out = HashMap<String, Float>()
        for (row in rows) {
            val f = ProgressRows.fraction(row) ?: continue
            if (row.mediaId != seriesId) out[row.mediaId] = f
            val meta = row.parsedMeta
            if (meta?.type == "series" && meta.mediaId == seriesId) {
                listOfNotNull(meta.id, meta.itemId).filter { it.isNotBlank() }.forEach { out.putIfAbsent(it, f) }
            }
        }
        return out
    }

    /** The unfinished row saved under the series id itself, or null (webui: completed → none). */
    fun continueRow(rows: List<ProgressRecord>, seriesId: String): ProgressRecord? {
        if (seriesId.isBlank()) return null
        val row = rows.firstOrNull { it.mediaId == seriesId } ?: return null
        return row.takeUnless { it.completed }
    }

    /** The loaded episode a continue row points at: by file/episode id, then by title. */
    fun findEpisode(meta: ProgressMeta?, episodes: List<MediaItem>): MediaItem? {
        if (meta == null) return null
        val ids = listOfNotNull(meta.id, meta.itemId, meta.playbackFileId).filter { it.isNotBlank() }.toSet()
        episodes.firstOrNull { it.id in ids }?.let { return it }
        val title = meta.episodeTitle?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return episodes.firstOrNull { it.displayTitle.trim().equals(title, ignoreCase = true) }
    }

    /** "Resume S1E3 · 12:34", "Resume <episode title> · 12:34" or "Resume · 12:34". */
    fun resumeLabel(meta: ProgressMeta?, episode: MediaItem?, resumeMs: Long?): String {
        val season = episode?.seriesNumber ?: meta?.series_number
        val code = Formatting.episodeCode(season, episode?.episodeNum)
        val what = code ?: meta?.episodeTitle?.trim()?.takeIf { it.isNotEmpty() && it != meta.title }
        val time = resumeMs?.takeIf { it > 0 }?.let { Formatting.clock(it) }
        return listOfNotNull(listOfNotNull("Resume", what).joinToString(" "), time).joinToString(" · ")
    }

    /** Season number for movie-link's `series` param: `number`, else digits in the name ("Season 2"), else position. */
    fun seasonNumber(season: MediaItem?, index: Int): Int {
        season?.number?.takeIf { it > 0 }?.let { return it }
        season?.displayTitle?.let { Regex("(\\d+)").find(it)?.value?.toIntOrNull() }?.let { return it }
        return index + 1
    }
}
