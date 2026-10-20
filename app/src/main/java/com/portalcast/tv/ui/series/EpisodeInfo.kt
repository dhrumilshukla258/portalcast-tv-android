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
    // Daily soaps carry the air date in the name: "2026-05-26 - Title", "25-07-2026 : Ep. 1294 - Title".
    private val ISO_DATE = Regex("""^\s*((?:19|20)\d{2})-(\d{1,2})-(\d{1,2})\s*(?:[.:\-–—|]\s*)?""")
    private val DMY_DATE = Regex("""^\s*(\d{1,2})[-./](\d{1,2})[-./]((?:19|20)\d{2})\s*(?:[.:\-–—|]\s*)?""")
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** The episode name after its number prefix ("Episode 13.", "S01E13", "13."). */
    private fun afterNumber(name: String): String = name
        .replace(SXXEYY, " ")
        .replaceFirst(LEADING_EPISODE, "")
        .replaceFirst(LEADING_NUMBER, "")
        .trim()

    /** "26 May 2026" when the name starts with an air date (after any episode prefix), else null. */
    fun airDate(ep: MediaItem): String? {
        val rest = afterNumber(ep.displayTitle.trim())
        ISO_DATE.find(rest)?.let { m ->
            val (y, mo, d) = m.destructured
            return format(d.toInt(), mo.toInt(), y)
        }
        DMY_DATE.find(rest)?.let { m ->
            val (d, mo, y) = m.destructured
            return format(d.toInt(), mo.toInt(), y)
        }
        return null
    }

    private fun format(day: Int, month: Int, year: String): String? =
        if (month in 1..12 && day in 1..31) "$day ${MONTHS[month - 1]} $year" else null

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

    /**
     * The title without its "Episode 13." / "S01E13" / "13." prefix and without a leading air
     * date (shown separately, see [airDate]). "Episode N" when nothing meaningful is left,
     * including a name that is only the show's own name ("2026-05-30 - Kamali").
     */
    fun title(ep: MediaItem, fallbackIndex: Int, seriesTitle: String? = null): String {
        var rest = afterNumber(ep.displayTitle.trim())
        rest = rest.replaceFirst(ISO_DATE, "").replaceFirst(DMY_DATE, "")
        // "25-07-2026 : Ep. 1294 - Title": a second episode marker after the date.
        rest = rest.replaceFirst(LEADING_EPISODE, "").replaceFirst(LEADING_NUMBER, "")
        val stripped = rest
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimStart('-', '–', '—', ':', '.', '|', ' ')
        val isShowName = seriesTitle != null && stripped.equals(seriesTitle.trim(), ignoreCase = true)
        return if (stripped.isEmpty() || isShowName) "Episode ${number(ep, fallbackIndex)}" else stripped
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
