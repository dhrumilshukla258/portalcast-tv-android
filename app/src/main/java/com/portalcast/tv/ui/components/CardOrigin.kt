package com.portalcast.tv.ui.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced

/**
 * Where the card that opened a title page was on screen, so the page grows out of that card and
 * shrinks back into it (owner 2026-10-29; the web app does the same: src/utils/cardOrigin.ts).
 * Recorded when a card is clicked, read once when the page opens: nothing per frame.
 */
object CardOrigin {
    /** The card's centre as fractions of the screen, and its width over the screen's. */
    data class Origin(val pivotX: Float, val pivotY: Float, val scale: Float)

    private var last: Origin? = null
    private var at = 0L

    internal fun set(origin: Origin) {
        last = origin
        at = SystemClock.uptimeMillis()
    }

    /** The card picked just now; a page opened later, or not from a card, has none. */
    fun take(): Origin? = last.takeIf { SystemClock.uptimeMillis() - at < 5_000 }.also { last = null }
}

/** A card's place on screen, kept by [trackOrigin] and turned into [CardOrigin] on click. */
class OriginTracker {
    private var coords: LayoutCoordinates? = null

    internal fun place(c: LayoutCoordinates) { coords = c }

    fun record() {
        val c = coords?.takeIf { it.isAttached } ?: return
        val root = c.findRootCoordinates().size
        if (root.width == 0 || root.height == 0) return
        val b = c.boundsInRoot()
        CardOrigin.set(
            CardOrigin.Origin(
                pivotX = (b.center.x / root.width).coerceIn(0f, 1f),
                pivotY = (b.center.y / root.height).coerceIn(0f, 1f),
                scale = (b.width / root.width).coerceIn(0.08f, 1f),
            ),
        )
    }
}

@Composable
fun rememberOriginTracker(): OriginTracker = remember { OriginTracker() }

fun Modifier.trackOrigin(tracker: OriginTracker): Modifier = onPlaced { tracker.place(it) }
