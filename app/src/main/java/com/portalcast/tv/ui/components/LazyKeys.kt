package com.portalcast.tv.ui.components

/**
 * Lazy-list keys that survive re-sorting and paging: the item's own id, with a suffix only
 * for a repeated id. Keys built from the list position ("3:id") change whenever a page is
 * appended and the list re-sorted, which disposes the focused row and throws focus to the
 * top of the list while scrolling.
 */
fun uniqueKeys(ids: List<String?>): List<String> {
    val seen = HashMap<String, Int>()
    return ids.mapIndexed { i, raw ->
        val id = raw?.takeIf { it.isNotBlank() } ?: "#$i"
        val n = (seen[id] ?: 0) + 1
        seen[id] = n
        if (n == 1) id else "$id#$n"
    }
}
