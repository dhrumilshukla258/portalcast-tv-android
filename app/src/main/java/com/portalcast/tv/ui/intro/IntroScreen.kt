package com.portalcast.tv.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.Group
import androidx.compose.ui.graphics.vector.Path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The cold-start intro (owner, 2026-10-24, "like Netflix's N"): the P appears in the middle,
 * its two signal arcs draw outward one after the other, then the P slides left while
 * "ortalcast" unfolds to its right into the logo, holds, and fades into the app. About 1.6 s.
 * Drawn from the logo's own numbers (res/drawable/portalcast_logo.xml, brand/build.sh), so the
 * last frame is the logo. [onDone] is called at the end; the caller skips it on any key.
 */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val p = remember { Animatable(0f) }        // the P: fade and zoom in
    val arc1 = remember { Animatable(0f) }     // inner signal arc drawn
    val arc2 = remember { Animatable(0f) }     // outer signal arc drawn
    val unfold = remember { Animatable(0f) }   // P to the left, letters revealed
    val out = remember { Animatable(1f) }      // everything fades into the app
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { p.animateTo(1f, tween(420, easing = LinearOutSlowInEasing)) }
            launch { delay(300); arc1.animateTo(1f, tween(280, easing = LinearOutSlowInEasing)) }
            launch { delay(440); arc2.animateTo(1f, tween(300, easing = LinearOutSlowInEasing)) }
        }
        unfold.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        delay(320)
        out.animateTo(0f, tween(260))
        onDone()
    }

    val gradient = remember { Brush.linearGradient(listOf(Color(0xFF22D3EE), Color(0xFF3B82F6), Color(0xFF7C5CFF)), start = Offset(16f, 10f), end = Offset(88f, 92f)) }
    val letters = remember { PathParser().parsePathString(LETTERS).toNodes() }
    val nodes = remember { SYMBOL.mapValues { PathParser().parsePathString(it.value).toNodes() } }
    // The P is at x 32 of the 398-wide logo: it starts in the middle (199).
    val slide = (1f - unfold.value) * (199f - 32f)
    val painter = rememberVectorPainter(defaultWidth = 398.dp, defaultHeight = 96.dp, viewportWidth = 398f, viewportHeight = 96f, autoMirror = false) { _, _ ->
        Group(translationX = -8f, translationY = -24f) {
            Group(translationX = 40f, translationY = 30f, scaleX = 1.1f, scaleY = 1.1f) {
                // "ortalcast" first, so the P passes over it: shown only right of the P's current
                // edge, a window that grows as the P moves left (the letters come out from behind it).
                if (unfold.value > 0f) {
                    // The P's right edge in the letters' own units (viewport x = 32 + 1.1 * (x - 12)).
                    // A little under the P (it hides that part), and by the end from before the "o" (x 25.5),
                    // so the last frame is the whole logo.
                    val from = (25.9f + slide) / 1.1f + 12f - 10f - 20f * unfold.value
                    val to = from + 345f * unfold.value
                    Group(translationX = -12f, clipPathData = PathParser().parsePathString("M${from},-20 H${to} V100 H${from} Z").toNodes()) {
                        Path(letters, stroke = SolidColor(Color(0xFFEAF2FF)), strokeLineWidth = 7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
                    }
                }
                // Symbol: translate(0 40) scale(.84) translate(-46 -52), slid right while it is alone.
                Group(
                    translationX = -38.64f + slide / 1.1f,
                    translationY = -3.68f,
                    scaleX = 0.84f * (0.85f + 0.15f * p.value),
                    scaleY = 0.84f * (0.85f + 0.15f * p.value),
                    pivotX = 46f, pivotY = 52f,
                ) {
                    Path(nodes.getValue("disc"), fill = SolidColor(Color(0xFF0B1226)), fillAlpha = p.value)
                    Path(nodes.getValue("stem"), stroke = gradient, strokeLineWidth = 12f, strokeLineCap = StrokeCap.Round, strokeAlpha = p.value)
                    Path(nodes.getValue("ring"), stroke = gradient, strokeLineWidth = 12f, strokeLineCap = StrokeCap.Round, strokeAlpha = p.value)
                    if (arc1.value > 0f) Path(nodes.getValue("arc1"), stroke = gradient, strokeLineWidth = 6.5f, strokeLineCap = StrokeCap.Round, trimPathEnd = arc1.value)
                    if (arc2.value > 0f) Path(nodes.getValue("arc2"), stroke = gradient, strokeLineWidth = 6.5f, strokeLineCap = StrokeCap.Round, strokeAlpha = 0.55f, trimPathEnd = arc2.value)
                    Path(nodes.getValue("play"), fill = SolidColor(Color.White), stroke = SolidColor(Color.White), strokeLineWidth = 4f, strokeLineJoin = StrokeJoin.Round, fillAlpha = p.value, strokeAlpha = p.value)
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF05070F)).graphicsLayer { alpha = out.value }, contentAlignment = Alignment.Center) {
        Image(painter, contentDescription = "PortalCast", modifier = Modifier.fillMaxWidth(0.42f))
    }
}

/** The symbol's parts (brand/src/symbol.svgfrag), as in res/drawable/portalcast_logo.xml. */
private val SYMBOL = mapOf(
    "disc" to "M29,52 a17,17 0 1,0 34,0 a17,17 0 1,0 -34,0",
    "stem" to "M24,52 V88",
    "ring" to "M66.67,44.48 A22,22 0 1,1 53.52,31.33",
    "arc1" to "M62.43,21.1 A35,35 0 0,1 76.9,35.57",
    "arc2" to "M71.05,13.42 A46,46 0 0,1 84.58,26.95",
    "play" to "M41.5,44 L55.5,52 L41.5,60 Z",
)

/** "ortalcast" (brand/src/word.svgfrag without its p), as in res/drawable/portalcast_logo.xml. */
private const val LETTERS = "M45,40 a16,16 0 1,0 32,0 a16,16 0 1,0 -32,0 M90,56 V24 M90,38 A14,14 0 0,1 104,24 M121,8 V46 A10,10 0 0,0 131,56 H133 M113,24 H131 M146,40 a16,16 0 1,0 32,0 a16,16 0 1,0 -32,0 M178,24 V56 M191,4 V56 M232.26,29.72 A16,16 0 1,0 232.26,50.28 M245,40 a16,16 0 1,0 32,0 a16,16 0 1,0 -32,0 M277,24 V56 M308.66,28 A10,8 0 1,0 300,40 A10,8 0 1,1 291.34,52 M323,8 V46 A10,10 0 0,0 333,56 H335 M315,24 H333"
