package com.portalcast.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch

/**
 * Scrolls only as far as needed to show the focused element, and not at all when it is already
 * on screen. Compose for TV's default puts the focused element about 30 % down the viewport,
 * which on a detail page scrolled the title off the top as soon as Play took focus (owner,
 * 2026-10-22: "the default screen hides the top, I can't read the name").
 */
@OptIn(ExperimentalFoundationApi::class)
private val MinimalBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = offset + size
        return when {
            offset >= 0f && end <= containerSize -> 0f
            // Taller than the screen (a detail page's facts block with a big logo and many facts):
            // its end, where the focused buttons are, stays on screen; showing its top hid Play
            // below the screen (owner, 2026-10-25: Sonic 2).
            size > containerSize -> end - containerSize
            offset < 0f -> offset
            else -> end - containerSize
        }
    }
}

/** Scrolling containers inside [content] scroll the minimum to show what gets focus. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MinimalScrolling(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalBringIntoView, content = content)
}

/**
 * When anything inside gets focus, the scrolling parent shows this whole element (heading, cards,
 * the titles under them and its padding), not just the focused card: More Like This at the bottom
 * of a detail page stopped at the card's edge with the titles below the screen (owner, 2026-10-22).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.showWholeWhenFocused(): Modifier {
    val size = remember { IntArray(2) }
    val responder = remember {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect =
                if (size[1] == 0) localRect else Rect(0f, 0f, size[0].toFloat(), size[1].toFloat())

            override suspend fun bringChildIntoView(localRect: () -> Rect?) = Unit
        }
    }
    return this.onSizeChanged { size[0] = it.width; size[1] = it.height }.bringIntoViewResponder(responder)
}

/**
 * A list that scrolls only when told: focus moving inside it never scrolls it. Home's rows use
 * it with [RowPinner]: the page's own bring-into-view requests (each card pinning its row to the
 * top) could not be met for the last rows, got stuck, and left rows that no longer scrolled
 * sideways, and a held Down restarted the animation on every repeat (owner, 2026-10-25).
 */
@OptIn(ExperimentalFoundationApi::class)
val NoBringIntoView: BringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/** Scrolls a lazy list so a row sits at its top; a new request replaces the running one. */
@androidx.compose.runtime.Stable
class RowPinner internal constructor(
    private val state: androidx.compose.foundation.lazy.LazyListState,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    private var job: kotlinx.coroutines.Job? = null
    fun show(index: Int) {
        job?.cancel()
        job = scope.launch { state.animateScrollToItem(index) }
    }
}

@Composable
fun rememberRowPinner(state: androidx.compose.foundation.lazy.LazyListState): RowPinner {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember(state, scope) { RowPinner(state, scope) }
}

/** On a row of a [RowPinner] list: when focus comes into the row, the list brings it to the top. */
fun Modifier.pinOnFocus(pinner: RowPinner, index: Int): Modifier =
    this.onFocusChanged { if (it.hasFocus) pinner.show(index) }

/**
 * A held Up / Down moves at most one row per [gapMs]: each step's scroll then plays out instead
 * of restarting on every key repeat (TV remotes repeat about every 50 ms).
 */
fun Modifier.paceHeldUpDown(gapMs: Long = 120L): Modifier = this.then(
    Modifier.onPreviewKeyEvent(object : (androidx.compose.ui.input.key.KeyEvent) -> Boolean {
        private var last = 0L
        override fun invoke(e: androidx.compose.ui.input.key.KeyEvent): Boolean {
            if (e.type != KeyEventType.KeyDown || (e.key != Key.DirectionDown && e.key != Key.DirectionUp)) return false
            val now = android.os.SystemClock.uptimeMillis()
            if (e.nativeKeyEvent.repeatCount > 0 && now - last < gapMs) return true
            last = now
            return false
        }
    }),
)
