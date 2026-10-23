package com.portalcast.tv.ui.components

import com.portalcast.tv.ui.components.rememberedIn
import com.portalcast.tv.ui.components.rememberFocusMemory
import com.portalcast.tv.ui.components.focusMemory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.ui.theme.AppColors

/**
 * A titled horizontal row of [MediaCard]s. Focus re-entering the row returns to the
 * card that last had it (see FocusMemory). [onLoadMore] fires when the last visible
 * card is within a few of the end (call it repeatedly safe: the caller de-dupes).
 *
 * @param progressFor watched fraction per item (Continue Watching), null for none.
 * @param subtitleFor small line under the title, defaults to the year.
 * @param itemModifier extra modifier per card (e.g. a `focusRequester` on index 0).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PosterRow(
    title: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    cardWidth: Dp = 150.dp,
    progressFor: (MediaItem) -> Float? = { null },
    subtitleFor: (MediaItem) -> String? = { it.displayYear },
    onLoadMore: (() -> Unit)? = null,
    itemModifier: (index: Int, item: MediaItem) -> Modifier = { _, _ -> Modifier },
    /** Detail pages: a small grey heading lined up with the text, like "Cast & crew". */
    compact: Boolean = false,
) {
    val loadMore by rememberUpdatedState(onLoadMore)
    if (onLoadMore != null) {
        LaunchedEffect(state) {
            snapshotFlow { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to state.layoutInfo.totalItemsCount }
                .collect { (last, total) -> if (total > 0 && last >= total - 4) loadMore?.invoke() }
        }
    }
    val memory = rememberFocusMemory()
    Column(modifier) {
        Text(
            title,
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge,
            color = if (compact) AppColors.TextMuted else AppColors.Text,
            modifier = if (compact) Modifier else Modifier.padding(start = 48.dp, bottom = 8.dp),
        )
        LazyRow(
            state = state,
            modifier = Modifier.focusMemory(memory),
            contentPadding = PaddingValues(horizontal = if (compact) 6.dp else 48.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(items, key = { i, item -> "$i:${item.id}" }) { i, item ->
                MediaCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    modifier = itemModifier(i, item).rememberedIn(memory),
                    subtitle = subtitleFor(item),
                    progress = progressFor(item),
                    width = cardWidth,
                )
            }
        }
    }
}
