package com.portalcast.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Looper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.Duration
import kotlin.math.roundToInt

/**
 * The cold-start intro (res/drawable/intro_logo.xml): the real AnimatedVectorDrawable is run
 * and drawn at set moments. The P is on screen from 100 ms to the fade (owner: an invisible P
 * read as "ortalcast"), starts in the middle and slides left, the word only comes out once it
 * moves, and the last frame is the logo (res/drawable/portalcast_logo.xml) at its place.
 * Frames go to build/intro-frames.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class IntroAvdTest {
    private val w = 960
    private val h = 540

    private fun symbolPixels(b: Bitmap, x0: Int, x1: Int): Int {
        var n = 0
        for (y in 0 until h step 2) for (x in x0 until x1 step 2) {
            val c = b.getPixel(x, y)
            val r = (c shr 16) and 0xFF; val bl = c and 0xFF
            if (bl > 150 && bl > r + 60) n++
        }
        return n
    }

    private fun letterPixels(b: Bitmap, x0: Int): Int {
        var n = 0
        for (y in 0 until h step 2) for (x in x0 until w step 2) {
            val c = b.getPixel(x, y)
            if (((c shr 16) and 0xFF) > 200 && ((c shr 8) and 0xFF) > 200 && (c and 0xFF) > 200) n++
        }
        return n
    }

    @Test
    fun plays_withThePVisible_andEndsOnTheLogo() {
        val ctx = RuntimeEnvironment.getApplication()
        // Robolectric runs animators at 0 duration unless told otherwise.
        org.robolectric.shadows.ShadowValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(null, 1f)
        // Frames only as the clock moves (by default each frame runs at once, so the whole
        // animation ran within the first step).
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        val avd = ctx.getDrawable(R.drawable.intro_logo) as AnimatedVectorDrawable
        avd.setBounds(0, 0, w, h)
        avd.start()
        val dir = File("build/intro-frames").apply { mkdirs() }
        val frames = HashMap<Int, Bitmap>()
        // It starts on the first frame after start() (as on the TV, where it starts when first
        // drawn), one step behind this clock: checks allow for that.
        var now = 0
        val missing = mutableListOf<Int>()
        for (ms in listOf(0, 100, 200, 300, 450, 600, 750, 900, 1000, 1100, 1300, 1500, 1700, 1900)) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis((ms - now).toLong()))
            now = ms
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            avd.draw(Canvas(b))
            frames[ms] = b
            File(dir, "avd$ms.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (ms in 200..1500 && symbolPixels(b, 0, w) <= 20) missing += ms
        }
        assertTrue("no P at $missing ms", missing.isEmpty())
        assertTrue("P not in the middle", symbolPixels(frames.getValue(300), w * 45 / 100, w * 56 / 100) > 20)
        assertTrue("letters before the slide", letterPixels(frames.getValue(600), w * 57 / 100) == 0)
        assertTrue("P not home", symbolPixels(frames.getValue(1500), 0, w * 36 / 100) > 20)

        // The last frame before the fade against the logo drawn where the intro puts it.
        val k = w * 0.42f / 398f
        val logo = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(logo).apply {
            drawColor(0xFF05070F.toInt())
            val d = ctx.getDrawable(R.drawable.portalcast_logo)!!
            val left = (w / 2f - 199f * k).roundToInt()
            val top = (h / 2f - 48f * k).roundToInt()
            d.setBounds(left, top, left + (398f * k).roundToInt(), top + (96f * k).roundToInt())
            d.draw(this)
        }
        File(dir, "logo.png").outputStream().use { logo.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Compared at half size (2x2 averages): the two are rasterised differently, so their
        // edges differ by a pixel; a real offset does not average out (2 px: about 900 here).
        val rest = frames.getValue(1500)
        fun half(b: Bitmap, x: Int, y: Int, sh: Int): Int =
            (((b.getPixel(2 * x, 2 * y) shr sh) and 0xFF) + ((b.getPixel(2 * x + 1, 2 * y) shr sh) and 0xFF) +
                ((b.getPixel(2 * x, 2 * y + 1) shr sh) and 0xFF) + ((b.getPixel(2 * x + 1, 2 * y + 1) shr sh) and 0xFF)) / 4
        var differ = 0
        for (y in 0 until h / 2) for (x in 0 until w / 2) {
            if (intArrayOf(16, 8, 0).any { sh -> kotlin.math.abs(half(rest, x, y, sh) - half(logo, x, y, sh)) > 60 }) differ++
        }
        assertTrue("last frame differs from the logo in $differ pixels", differ < 450)
    }
}
