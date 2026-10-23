package com.portalcast.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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
