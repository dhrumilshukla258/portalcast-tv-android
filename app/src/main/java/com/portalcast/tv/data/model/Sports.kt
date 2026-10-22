package com.portalcast.tv.data.model

import kotlinx.serialization.Serializable

/**
 * `GET /api/v2/sports?days=` (servers from 2026-10-22): upcoming games of the leagues the admin
 * chose, from sporting-events.org (credited on the screen), with the channels the guide shows
 * them on (strict: both teams named at kick-off). Channel ids only: names and logos come from
 * the channel list.
 */
@Serializable
data class SportsResponse(
    val source: SportsSource = SportsSource(),
    val updatedAt: Long? = null,
    /** True while the server's first fetch is still running: ask again shortly. */
    val loading: Boolean = false,
    /** False when the server has no current guide, so no game can show a channel. */
    val guide: Boolean = false,
    val leagues: List<SportsLeague> = emptyList(),
    val games: List<SportsGame> = emptyList(),
)

@Serializable
data class SportsSource(val name: String = "sporting-events.org", val url: String = "https://sporting-events.org/")

@Serializable
data class SportsLeague(val id: String = "", val name: String = "", val sport: String = "", val url: String = "")

@Serializable
data class SportsGame(
    val id: String = "",
    val league: String = "",
    val competition: String = "",
    val sport: String = "",
    /** UTC date, "2026-10-24". */
    val date: String = "",
    /** Kick-off in ms since the epoch; null when the time is not known yet. */
    val start: Long? = null,
    val title: String = "",
    val home: String = "",
    val away: String = "",
    val venue: String = "",
    val status: String = "",
    val broadcast: List<String> = emptyList(),
    val url: String = "",
    val live: Boolean = false,
    val channels: List<SportsChannel> = emptyList(),
)

/** `GET /api/v2/sports/leagues`: every league there is, and the viewer's choice. */
@Serializable
data class SportsLeagues(
    val leagues: List<SportsLeagueInfo> = emptyList(),
    val chosen: List<String> = emptyList(),
    val defaults: List<String> = emptyList(),
    val max: Int = 20,
)

@Serializable
data class SportsLeagueInfo(val id: String = "", val name: String = "", val sport: String = "")

@Serializable
data class SportsChannel(val channelId: String = "", val programme: String = "")
