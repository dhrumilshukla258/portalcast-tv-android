package com.portalcast.tv.data.repo

import com.portalcast.tv.data.model.ChannelGroup
import com.portalcast.tv.data.model.Paged

/** `*` is what the server wants when no category is known (contract §3.2, §3.3). */
internal const val ALL_CATEGORY = ChannelGroup.ALL_ID

/** `/movies` and `/series` must always carry `category`; blank or null becomes `*`. */
internal fun categoryOrAll(category: String?): String = category?.trim()?.takeIf { it.isNotEmpty() } ?: ALL_CATEGORY

/** The synthetic "All" group the webui prepends to VOD and series categories. */
internal fun withAllGroup(groups: List<ChannelGroup>): List<ChannelGroup> =
    listOf(ChannelGroup.synthetic(ALL_CATEGORY, "All")) + groups.filterNot { it.id == ALL_CATEGORY }

/**
 * Category-scoped lookup with the webui's stale-category fallback: if [first]
 * yields nothing and was not already `*`, try again with `*`. Returns the page
 * and the category that produced it.
 */
internal suspend fun <T> lookupWithAllFallback(first: String, call: suspend (String) -> Paged<T>): Pair<Paged<T>, String> {
    val page = call(first)
    if (page.data.isNotEmpty() || first == ALL_CATEGORY) return page to first
    return call(ALL_CATEGORY) to ALL_CATEGORY
}
