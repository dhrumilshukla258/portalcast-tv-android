@file:OptIn(ExperimentalFoundationApi::class)

package com.portalcast.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch

/**
 * Up/Down in a poster grid keeps its column, however fast the remote repeats.
 *
 * Holding Down outran composition: the row below was not laid out yet, the default focus
 * search found nothing under the focused card, and focus landed on the first card of the
 * row that did get composed (column 1). Here, when the card straight above/below is not
 * on screen, the grid is scrolled by one row first and that exact card is focused; repeats
 * that arrive while this is in flight are swallowed instead of piling up.
 */
@Stable
class GridFocus(val state: LazyGridState) {
    internal var focused = -1
    internal var pending = false
    private val requesters = HashMap<Int, FocusRequester>()
    internal fun requester(index: Int): FocusRequester = requesters.getOrPut(index) { FocusRequester() }
}

@Composable
fun rememberGridFocus(state: LazyGridState): GridFocus = remember(state) { GridFocus(state) }

/** On each grid item: its position, so the grid knows which card is focused. */
fun Modifier.gridItem(grid: GridFocus, index: Int): Modifier =
    focusRequester(grid.requester(index)).onFocusChanged { if (it.isFocused) grid.focused = index }

/** On the grid itself. */
@Composable
fun Modifier.keepColumn(grid: GridFocus, itemCount: Int): Modifier {
    val scope = rememberCoroutineScope()
    return onPreviewKeyEvent { ev ->
        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val dir = when (ev.key) {
            Key.DirectionDown -> 1
            Key.DirectionUp -> -1
            else -> return@onPreviewKeyEvent false
        }
        if (grid.pending) return@onPreviewKeyEvent true
        val info = grid.state.layoutInfo
        val visible = info.visibleItemsInfo
        val columns = (visible.maxOfOrNull { it.column } ?: -1) + 1
        val from = grid.focused
        if (columns <= 0 || from < 0) return@onPreviewKeyEvent false
        val target = from + dir * columns
        if (target !in 0 until itemCount) return@onPreviewKeyEvent false
        val shown = visible.firstOrNull { it.index == target }
        if (shown != null && shown.offset.y >= info.viewportStartOffset && shown.offset.y + shown.size.height <= info.viewportEndOffset) {
            return@onPreviewKeyEvent false // on screen: the normal focus move is right
        }
        val rowHeight = (visible.firstOrNull { it.index == from }?.size?.height ?: visible.firstOrNull()?.size?.height ?: 0) +
            info.mainAxisItemSpacing
        grid.pending = true
        scope.launch {
            try {
                if (shown == null && rowHeight > 0) grid.state.scrollBy(dir * rowHeight.toFloat())
                repeat(10) {
                    withFrameNanos { }
                    if (runCatching { grid.requester(target).requestFocus() }.isSuccess) return@launch
                }
            } finally {
                grid.pending = false
            }
        }
        true
    }
}

/**
 * Bring-into-view for a grid card: the focused node is the poster only, so by default the
 * grid scrolled just far enough to show the unscaled poster, leaving its focus scale and
 * the title under it cut off at the screen edge. The rect is widened above and below.
 */
private class RevealCardWithTitle(private val topPx: Float, private val bottomPx: Float) : BringIntoViewResponder {
    override fun calculateRectForParent(localRect: Rect): Rect =
        Rect(localRect.left, localRect.top - topPx, localRect.right, localRect.bottom + bottomPx)

    override suspend fun bringChildIntoView(localRect: () -> Rect?) = Unit
}

/** On each card of a poster grid (with [gridItem]): scrolls the whole card and its title into view. */
@Composable
fun Modifier.revealWithTitle(): Modifier {
    val density = LocalDensity.current
    val responder = remember(density) { with(density) { RevealCardWithTitle(24.dp.toPx(), 72.dp.toPx()) } }
    return bringIntoViewResponder(responder)
}
