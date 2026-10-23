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
            offset < 0f || size > containerSize -> offset
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
