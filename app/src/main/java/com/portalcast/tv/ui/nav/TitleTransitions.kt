package com.portalcast.tv.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigation.NavBackStackEntry
import com.portalcast.tv.ui.components.CardOrigin

/**
 * A title's page grows out of the card picked and shrinks back into it on Back (owner
 * 2026-10-29: "like the title expanding", the same in the web app: TitlePageLayer.tsx, with the
 * same times and curves). Scale and alpha only (graphics layer): nothing recomposes per frame.
 * The page under it stays as it is while the title grows over it.
 */
internal object TitleTransitions {
    private const val OPEN_MS = 380
    private const val CLOSE_MS = 260
    // Nav's own default for the other screens.
    private const val DEFAULT_MS = 700
    private val EaseOpen = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val EaseClose = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    private val titleRoutes = setOf(Routes.MOVIE_DETAIL, Routes.SERIES_DETAIL)
    // Each open title's card, for shrinking back into it (by back stack entry).
    private val origins = HashMap<String, CardOrigin.Origin?>()

    private fun isTitle(entry: NavBackStackEntry) = entry.destination.route in titleRoutes

    private fun pivot(o: CardOrigin.Origin?) = o?.let { TransformOrigin(it.pivotX, it.pivotY) } ?: TransformOrigin.Center

    /** A title page opening. */
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        val o = origins.getOrPut(targetState.id) { CardOrigin.take() }
        scaleIn(tween(OPEN_MS, easing = EaseOpen), initialScale = o?.scale ?: 0.92f, transformOrigin = pivot(o)) +
            fadeIn(tween(OPEN_MS / 2))
    }

    /** A title page closing (Back). */
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        val o = origins.remove(initialState.id)
        scaleOut(tween(CLOSE_MS, easing = EaseClose), targetScale = o?.scale ?: 0.92f, transformOrigin = pivot(o)) +
            fadeOut(tween(CLOSE_MS, easing = EaseClose))
    }

    /** A screen titles open from (Home, a title, a person): stays under the title growing. */
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (isTitle(targetState)) ExitTransition.KeepUntilTransitionsFinished else fadeOut(tween(DEFAULT_MS))
    }

    /** ...and is simply there again under the title shrinking away. */
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (isTitle(initialState)) EnterTransition.None else fadeIn(tween(DEFAULT_MS))
    }
}
