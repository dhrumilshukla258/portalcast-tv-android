package com.portalcast.tv.ui.intro

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws the intro at set moments (Robolectric's native graphics, the app's own draw function)
 * and checks the P is on screen in every frame from 100 ms to the fade (owner: "keep the p",
 * an invisible P read as "ortalcast"). Frames are written to build/intro-frames to look at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IntroFramesTest {
    private val w = 960
    private val h = 540

    private fun frame(shapes: IntroShapes, t: Float): Bitmap {
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) { drawIntro(shapes, t) }
        return image.asAndroidBitmap()
    }

    /** Pixels of the P's gradient (blue well above red) between x0 and x1. */
    private fun symbolPixels(b: Bitmap, x0: Int, x1: Int): Int {
        var n = 0
        for (y in 0 until b.height step 2) for (x in x0 until x1 step 2) {
            val c = b.getPixel(x, y)
            val r = (c shr 16) and 0xFF; val bl = c and 0xFF
            if (bl > 150 && bl > r + 60) n++
        }
        return n
    }

    /** Near-white pixels (the letters) right of x0. */
    private fun letterPixels(b: Bitmap, x0: Int): Int {
        var n = 0
        for (y in 0 until b.height step 2) for (x in x0 until b.width step 2) {
            val c = b.getPixel(x, y)
            if (((c shr 16) and 0xFF) > 200 && ((c shr 8) and 0xFF) > 200 && (c and 0xFF) > 200) n++
        }
        return n
    }

    @Test
    fun theP_isVisibleInEveryFrame_andEndsAtTheLogoPlace() {
        val shapes = IntroShapes()
        val dir = File("build/intro-frames").apply { mkdirs() }
        for (ms in listOf(0, 100, 200, 300, 450, 600, 750, 900, 1000, 1100, 1260, 1500, 1600, 1700, 1840)) {
            val b = frame(shapes, ms.toFloat())
            File(dir, "f$ms.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (ms in 100..1600) assertTrue("no P at $ms ms", symbolPixels(b, 0, w) > 20)
        }
        // At the start the P is in the middle; at rest at the logo's left (x 32 of 398, the logo
        // 42% of the width and centred), with the word to its right.
        assertTrue("P not in the middle", symbolPixels(frame(shapes, 300f), w * 45 / 100, w * 56 / 100) > 20)
        val rest = frame(shapes, 1500f)
        assertTrue("P not home", symbolPixels(rest, 0, w * 36 / 100) > 20)
        assertTrue("no letters", letterPixels(rest, w * 36 / 100) > 200)
        // Right of the P (its play triangle is white too).
        assertTrue("letters before the slide", letterPixels(frame(shapes, 600f), w * 57 / 100) == 0)
    }
}
