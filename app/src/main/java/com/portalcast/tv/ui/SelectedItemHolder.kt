package com.portalcast.tv.ui

import com.portalcast.tv.data.model.MediaItem

/**
 * Process-wide hand-off of the list item a detail screen was opened from (same idea
 * as `PlaybackHolder`): routes carry only `id` + `category`, and there is no
 * single-item route, so the detail screen reads title/art/overview from here and
 * falls back to the resolved file when empty (process death, deep link).
 *
 * Call [put] right before navigating to `movie/{id}` or `series/{id}`.
 */
object SelectedItemHolder {
    private const val MAX = 32
    private val items = LinkedHashMap<String, MediaItem>()

    fun put(item: MediaItem) {
        if (item.id.isBlank()) return
        synchronized(items) {
            items.remove(item.id)
            items[item.id] = item
            while (items.size > MAX) items.remove(items.keys.first())
        }
    }

    /** The item last put under [id] (or whose raw, unprefixed id is [id]), or null. */
    fun get(id: String): MediaItem? = synchronized(items) {
        items[id] ?: items.values.lastOrNull { it.rawId == id }
    }
}
