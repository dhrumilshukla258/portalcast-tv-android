package com.portalcast.tv.ui.series

import com.portalcast.tv.data.model.MediaItem

/**
 * Episode/season numbering and titles that hold up on real portals. Stalker portals (seen
 * on the owner's) leave `episode_num` empty, put the episode number only in the name
 * ("Episode 13. Friends and Family") and in `series_number`, and list episodes and seasons
 * newest first. Numbering by list position therefore showed "1. Episode 13. …" and made
 * "next episode" go backwards. Pure, JVM-tested.
 */
object EpisodeInfo {

    // "Episode 13. Title", "Ep 13 - Title", "E13: Title", "S01E13 Title", "13. Title"
    private val SXXEYY = Regex("""\bS\d{1,2}\s*E(\d{1,4})\b""", RegexOption.IGNORE_CASE)
    private val LEADING_EPISODE = Regex("""^\s*(?:episode|ep\.?|e)\s*(\d{1,4})\s*(?:[.:\-–—)]\s*)?""", RegexOption.IGNORE_CASE)
    private val LEADING_NUMBER = Regex("""^\s*(\d{1,4})\s*[.:\-–—)]\s+""")
    private val SEASON_IN_NAME = Regex("""\bseason\s*(\d{1,3})\b""", RegexOption.IGNORE_CASE)

    /** The episode's number: from its name, then `episode_num`, then its position (1-based). */
    fun number(ep: MediaItem, fallbackIndex: Int): Int {
        val name = ep.displayTitle
        SXXEYY.find(name)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        LEADING_EPISODE.find(name)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        LEADING_NUMBER.find(name)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        ep.episodeNum?.takeIf { it > 0 }?.let { return it }
        return fallbackIndex + 1
    }

    /** True when the number came from the episode itself rather than its list position. */
    fun hasOwnNumber(ep: MediaItem): Boolean {
        val name = ep.displayTitle
        return SXXEYY.containsMatchIn(name) || LEADING_EPISODE.containsMatchIn(name) || LEADING_NUMBER.containsMatchIn(name) ||
            (ep.episodeNum ?: 0) > 0
    }

    /** The title without its "Episode 13." / "S01E13" / "13." prefix; "Episode N" when nothing is left. */
    fun title(ep: MediaItem, fallbackIndex: Int): String {
        val name = ep.displayTitle.trim()
        val stripped = name
            .replace(SXXEYY, " ")
            .replaceFirst(LEADING_EPISODE, "")
            .replaceFirst(LEADING_NUMBER, "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimStart('-', '–', '—', ':', '.', ' ')
        return stripped.ifEmpty { "Episode ${number(ep, fallbackIndex)}" }
    }

    /** Episodes in ascending episode order (stable for equal numbers). */
    fun sortEpisodes(episodes: List<MediaItem>): List<MediaItem> =
        episodes.withIndex().sortedWith(compareBy({ number(it.value, it.index) }, { it.index })).map { it.value }

    /** Season number: `number`, else "Season N" in the name, else the first number in the name, else position. */
    fun seasonNumber(season: MediaItem, index: Int): Int {
        season.number?.takeIf { it > 0 }?.let { return it }
        SEASON_IN_NAME.find(season.displayTitle)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("""(\d+)""").find(season.displayTitle)?.value?.toIntOrNull()?.let { return it }
        return index + 1
    }

    /** "Season 5" for tabs (portals send "Season 5. Kim's Convenience : Season 05"). */
    fun seasonLabel(season: MediaItem, index: Int): String = "Season ${seasonNumber(season, index)}"

    /** Seasons in ascending order. */
    fun sortSeasons(seasons: List<MediaItem>): List<MediaItem> =
        seasons.withIndex().sortedWith(compareBy({ seasonNumber(it.value, it.index) }, { it.index })).map { it.value }
}
