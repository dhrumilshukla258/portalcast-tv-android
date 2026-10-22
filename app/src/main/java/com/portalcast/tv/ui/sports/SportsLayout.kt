package com.portalcast.tv.ui.sports

import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.SportsGame
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Sports tab's grouping and labels. Pure, JVM-tested. */
object SportsLayout {

    data class Day(val date: LocalDate, val label: String, val games: List<SportsGame>)

    /** The sports in the answer, in first-seen order (the server sends games by kick-off). */
    fun sportsOf(games: List<SportsGame>): List<String> = games.map { it.sport }.filter { it.isNotBlank() }.distinct()

    /** The viewer's day for a game: kick-off in [zone]; a game without a time keeps its date. */
    fun localDate(game: SportsGame, zone: ZoneId): LocalDate? =
        game.start?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            ?: runCatching { LocalDate.parse(game.date) }.getOrNull()

    /** Games of [sport] (null: all) by the viewer's day, live games first within a day. */
    fun days(games: List<SportsGame>, sport: String?, zone: ZoneId, today: LocalDate): List<Day> =
        games.asSequence()
            .filter { sport == null || it.sport == sport }
            .mapNotNull { g -> localDate(g, zone)?.let { it to g } }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (date, list) -> Day(date, dayLabel(date, today), list.sortedByDescending { it.live }) }

    fun dayLabel(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    }

    /** "15:00" / "3:00 PM", or "Time TBC" when the source has no time yet. */
    fun timeLabel(game: SportsGame, zone: ZoneId, use24h: Boolean, locale: Locale = Locale.getDefault()): String {
        val start = game.start ?: return "Time TBC"
        val pattern = if (use24h) "HH:mm" else "h:mm a"
        return Instant.ofEpochMilli(start).atZone(zone).format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    /** "Premier League · Liverpool, England". */
    fun metaLine(game: SportsGame): String = listOf(game.competition, game.venue).filter { it.isNotBlank() }.joinToString(" · ")

    /** The game's guide-matched channels that are in the viewer's channel list, in the server's order. */
    fun channelsOf(game: SportsGame, byId: Map<String, Channel>): List<Channel> =
        game.channels.mapNotNull { byId[it.channelId] }.distinctBy { it.id }

    /** What "Find channel" searches Live TV for: the first broadcaster, without "(US)"-style notes. */
    fun findQuery(game: SportsGame): String? =
        game.broadcast.firstNotNullOfOrNull { b -> b.replace(Regex("""\s*\([^)]*\)"""), "").trim().takeIf { it.length >= 2 } }
}
