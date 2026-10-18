package com.portalcast.tv.ui.components

import com.portalcast.tv.data.model.Paged

/**
 * Accumulates the server's fixed 14-item pages (`/movies`, `/series`, episodes) for an
 * infinite grid or list. Immutable: [append] returns the next value, so it can live in
 * a ViewModel's StateFlow. Duplicates (by `key`) across pages are dropped.
 */
data class PagedList<T>(
    val items: List<T> = emptyList(),
    /** The page to request next (1-based). */
    val nextPage: Int = 1,
    /** True once the server said there is nothing more (empty page, no new items, or total reached). */
    val endReached: Boolean = false,
    val totalItems: Int? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty()

    /** True after at least one page came back. */
    val hasLoaded: Boolean get() = nextPage > 1

    fun append(page: Paged<T>, key: (T) -> String): PagedList<T> {
        val seen = items.mapTo(HashSet()) { key(it) }
        val fresh = page.data.filter { seen.add(key(it)) }
        val merged = items + fresh
        val total = page.totalItems
        val end = page.data.isEmpty() || fresh.isEmpty() || (total != null && total > 0 && merged.size >= total)
        return PagedList(items = merged, nextPage = nextPage + 1, endReached = end, totalItems = total)
    }

    /** Whether a list showing up to [lastVisibleIndex] is close enough to the end to fetch the next page. */
    fun shouldLoadMore(lastVisibleIndex: Int, threshold: Int = DEFAULT_THRESHOLD): Boolean =
        !endReached && lastVisibleIndex >= items.size - 1 - threshold

    companion object {
        const val PAGE_SIZE = 14
        const val DEFAULT_THRESHOLD = 7
    }
}
