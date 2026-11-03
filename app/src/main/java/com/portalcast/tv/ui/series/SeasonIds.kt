package com.portalcast.tv.ui.series

/**
 * True when a season in a series' list is the season [wanted] names. A show the provider lists
 * one season at a time is served as one show (server: content/seasonParts.ts), and its seasons'
 * ids name the provider seasons they come from ("m:7:574273.7~515872.1"), so progress saved
 * before that, with a provider season id ("1"), still finds its season (web: utils/seasonIds.ts).
 */
object SeasonIds {
    private val MERGED = Regex("""^m:\d+:(.+)$""")

    fun matches(listId: String?, wanted: String?): Boolean {
        if (wanted.isNullOrEmpty() || listId == null) return false
        if (listId == wanted) return true
        val sources = MERGED.find(listId)?.groupValues?.get(1) ?: return false
        return sources.split('~', '+', ' ').any { it.substringAfter('.', "") == wanted }
    }

    /** The season [wanted] names: its own id first, else a merged season holding it. */
    fun <T> find(seasons: List<T>, wanted: String?, id: (T) -> String?): T? =
        seasons.firstOrNull { id(it) == wanted } ?: seasons.firstOrNull { matches(id(it), wanted) }
}
