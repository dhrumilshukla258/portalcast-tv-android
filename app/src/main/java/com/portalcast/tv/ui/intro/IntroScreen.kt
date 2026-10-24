package com.portalcast.tv.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser

/**
 * The cold-start intro (owner, 2026-10-24, "like Netflix's N"): the P appears in the middle,
 * its two signal arcs draw outward one after the other, then the P slides left while
 * "ortalcast" comes out from behind it into the logo, holds, and fades into the app. About
 * 1.8 s; the web app plays the same one (portalcast-webui index.html), from the logo's own
 * numbers (brand/build.sh), so the last frame is the logo.
 *
 * Drawn on a Canvas from shapes made once ([IntroShapes]); a frame only reads the clock in the
 * draw phase, so nothing recomposes and nothing is allocated while it plays (60 fps on the TV;
 * the first version rebuilt a vector image and parsed a clip path every frame). [onDone] is
 * called at the end; the caller skips it on any key.
 */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        time.animateTo(INTRO_MS, tween(INTRO_MS.toInt(), easing = LinearEasing))
        onDone()
    }
    IntroCanvas { time.value }
}

/** The intro at [timeMs] (read when drawing). Separate so tests can draw any moment. */
@Composable
internal fun IntroCanvas(timeMs: () -> Float) {
    val shapes = remember { IntroShapes() }
    Canvas(Modifier.fillMaxSize()) { drawIntro(shapes, timeMs()) }
}

internal const val INTRO_MS = 1840f

/** Paths, measures, brush and strokes: everything a frame needs, made once. */
internal class IntroShapes {
    private fun path(d: String): Path = PathParser().parsePathString(d).toPath()
    val disc = path("M29,52 a17,17 0 1,0 34,0 a17,17 0 1,0 -34,0")
    val stem = path("M24,52 V88")
    val ring = path("M66.67,44.48 A22,22 0 1,1 53.52,31.33")
    val play = path("M41.5,44 L55.5,52 L41.5,60 Z")
    private val arc1 = path("M62.43,21.1 A35,35 0 0,1 76.9,35.57")
    private val arc2 = path("M71.05,13.42 A46,46 0 0,1 84.58,26.95")
    /** "ortalcast" (brand/src/word.svgfrag without its p). */
    val letters = path(
        "M45,40 a16,16 0 1,0 32,0 a16,16 0 1,0 -32,0 M90,56 V24 M90,38 A14,14 0 0,1 104,24 M121,8 V46 A10,10 0 0,0 131,56 H133 M113,24 H131 " +
            "M146,40 a16,16 0 1,0 32,0 a16,16 0 1,0 -32,0 M178,24 V56 M191,4 V56 M232.26,29.72 A16,16 0 1,0 232.26,50.28 " +
            "M245,40 a16,16 0 1,0 32,0 a16,16 0 1,0 -32,0 M277,24 V56 M308.66,28 A10,8 0 1,0 300,40 A10,8 0 1,1 291.34,52 " +
            "M323,8 V46 A10,10 0 0,0 333,56 H335 M315,24 H333",
    )
    private val measure1 = PathMeasure().apply { setPath(arc1, false) }
    private val measure2 = PathMeasure().apply { setPath(arc2, false) }
    private val len1 = measure1.length
    private val len2 = measure2.length
    /** The drawn part of each arc, refilled per frame (reset, not reallocated). */
    val arc1Part = Path()
    val arc2Part = Path()
    fun trim(part: Path, which: Int, fraction: Float) {
        part.reset()
        if (which == 1) measure1.getSegment(0f, len1 * fraction, part, true) else measure2.getSegment(0f, len2 * fraction, part, true)
    }
    val gradient = Brush.linearGradient(listOf(Color(0xFF22D3EE), Color(0xFF3B82F6), Color(0xFF7C5CFF)), start = Offset(16f, 10f), end = Offset(88f, 92f))
    val thick = Stroke(width = 12f, cap = StrokeCap.Round)
    val thin = Stroke(width = 6.5f, cap = StrokeCap.Round)
    val playStroke = Stroke(width = 4f, join = StrokeJoin.Round)
    val letterStroke = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
}

private val Background = Color(0xFF05070F)
private val Disc = Color(0xFF0B1226)
private val LetterColor = Color(0xFFEAF2FF)

private fun phase(t: Float, start: Float, duration: Float, easing: Easing? = null): Float {
    val x = ((t - start) / duration).coerceIn(0f, 1f)
    return easing?.transform(x) ?: x
}

/** One frame; the timings and positions are the web intro's. */
internal fun DrawScope.drawIntro(s: IntroShapes, t: Float) {
    val p = phase(t, 0f, 420f, LinearOutSlowInEasing)        // the P: fade and zoom in
    val r1 = phase(t, 300f, 280f, LinearOutSlowInEasing)     // inner signal arc drawn
    val r2 = phase(t, 440f, 300f, LinearOutSlowInEasing)     // outer signal arc drawn
    val u = phase(t, 740f, 520f, FastOutSlowInEasing)        // P to the left, letters revealed
    val out = 1f - phase(t, 1580f, 260f)                     // everything fades into the app
    drawRect(Background, alpha = out)
    if (out <= 0f) return

    // The logo (viewBox 8 24 398 96) is 42% of the screen's width, centred.
    val k = size.width * 0.42f / 398f
    // The P is at x 32 of the logo: it starts in the middle (199) and slides home.
    val slide = (1f - u) * (199f - 32f) / 1.1f
    withTransform({
        translate(size.width / 2f - 199f * k, size.height / 2f - 48f * k)
        scale(k, k, pivot = Offset.Zero)
        translate(-8f + 40f, -24f + 30f)
        scale(1.1f, 1.1f, pivot = Offset.Zero)
    }) {
        // "ortalcast" first, so the P passes over it: shown only right of the P's edge, a window
        // that grows as the P moves left (the letters come out from behind it).
        if (u > 0f) translate(-12f, 0f) {
            val from = (25.9f + slide * 1.1f) / 1.1f + 12f - 10f - 20f * u
            clipRect(from, -20f, from + 345f * u, 100f) { drawPath(s.letters, LetterColor, alpha = out, style = s.letterStroke) }
        }
        // The symbol: translate(0 40) scale(.84) translate(-46 -52), slid right while alone.
        val z = 0.84f * (0.85f + 0.15f * p)
        withTransform({
            translate(slide, 40f)
            scale(z, z, pivot = Offset.Zero)
            translate(-46f, -52f)
        }) {
            val a = p * out
            drawPath(s.disc, Disc, alpha = a, style = Fill)
            drawPath(s.stem, s.gradient, alpha = a, style = s.thick)
            drawPath(s.ring, s.gradient, alpha = a, style = s.thick)
            if (r1 > 0f) { s.trim(s.arc1Part, 1, r1); drawPath(s.arc1Part, s.gradient, alpha = out, style = s.thin) }
            if (r2 > 0f) { s.trim(s.arc2Part, 2, r2); drawPath(s.arc2Part, s.gradient, alpha = 0.55f * out, style = s.thin) }
            drawPath(s.play, Color.White, alpha = a, style = Fill)
            drawPath(s.play, Color.White, alpha = a, style = s.playStroke)
        }
    }
}
