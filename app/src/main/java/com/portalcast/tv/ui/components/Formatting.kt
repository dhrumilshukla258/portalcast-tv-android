package com.portalcast.tv.ui.components

import com.portalcast.tv.data.model.MediaItem

/** Small display-string helpers shared by the browse and detail screens. Pure, JVM-tested. */
object Formatting {

    /** `mm:ss` under an hour, `h:mm:ss` from an hour up. Negative input clamps to `00:00`. */
    fun clock(ms: Long): String {
        val totalSec = ms.coerceAtLeast(0L) / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }

    /** "Resume from 12:34". */
    fun resumeFrom(ms: Long): String = "Resume from ${clock(ms)}"

    /** "S1E3", "E3" when the season is unknown, null when the episode number is unknown. */
    fun episodeCode(season: Int?, episode: Int?): String? = when {
        episode == null -> null
        season == null -> "E$episode"
        else -> "S${season}E$episode"
    }

    /** "1h 52m" / "45m" from minutes; null for zero or less. */
    fun minutes(min: Int?): String? {
        if (min == null || min <= 0) return null
        val h = min / 60
        val m = min % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** Best-effort runtime for a movie or episode: `runtime`/`time` minutes, else the free-form `duration`. */
    fun runtime(item: MediaItem): String? =
        minutes(item.runtime) ?: minutes(item.time?.trim()?.toIntOrNull())
            ?: item.duration?.trim()?.takeUnless { it.isEmpty() || it == "0" || it == "-" || it == "00:00:00" }

    /** "2019 · ★ 7.4 · Drama, Action · 1h 52m", skipping whatever is missing. */
    fun metaLine(item: MediaItem): String = listOfNotNull(
        item.displayYear,
        item.displayRating?.let { "★ $it" },
        item.displayGenres,
        runtime(item),
    ).joinToString("  ·  ")
}
