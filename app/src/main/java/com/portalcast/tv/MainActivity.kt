package com.portalcast.tv

import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.core.view.doOnPreDraw
import com.portalcast.tv.ui.nav.AppNavHost
import com.portalcast.tv.ui.theme.PortalcastTheme

/** The intro's length (res/drawable/intro_logo.xml, tools/gen_intro_avd.py). */
private const val INTRO_MS = 1840L

/** When the logo has stopped moving (the slide ends at 1.26 s): the app's screens are built then. */
private const val APP_AFTER_MS = 1300L

class MainActivity : ComponentActivity() {
    /** The intro over the app while it plays; null once it is gone. */
    private var intro: ImageView? = null

    /** The intro's tone (res/raw/intro_tone.wav, tools/gen_intro_tone.py), at the media volume. */
    private var tone: MediaPlayer? = null

    /**
     * False while the intro's logo moves. A trace on the owner's TV (2026-10-24) showed the intro
     * drawn only 23 times in 4 s, with gaps of up to 0.6 s exactly while the main thread built
     * Home (0.27 s, then 0.56 s): even on the RenderThread the animation waits for that work. So
     * the app is composed once the slide is over, during the hold and the fade.
     */
    private val appReady = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 7.1+ only: older versions draw no gradients in a vector drawable (the P and
        // its arcs would be invisible) and run the animation on the main thread. They open
        // straight into the app.
        // Every time the app is opened (owner, 2026-10-24: also after exiting with Back, when the
        // process lived on): a new screen, not one Android restores (savedInstanceState), which
        // is a return to where the viewer was (Home button, a background restart).
        val playIntro = Build.VERSION.SDK_INT >= 25 && savedInstanceState == null
        appReady.value = !playIntro
        setContent {
            PortalcastTheme {
                // The app loads underneath while the intro plays; any key skips it (and is not passed on).
                Box(Modifier.fillMaxSize().onPreviewKeyEvent { if (intro != null) { endIntro(); true } else false }) {
                    if (appReady.value) AppNavHost(container = appContainer)
                }
            }
        }
        if (playIntro) {
            // An AnimatedVectorDrawable in a plain View, not Compose: Android runs it on the
            // RenderThread, so it stays smooth while the main thread builds the first screen.
            // Drawn with Compose it got 8 frames in 1.8 s on the TV (each 0.3-2 s: startup work
            // held the main thread), so the P popped in and the word appeared without motion.
            val view = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setImageResource(R.drawable.intro_logo)
            }
            addContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            intro = view
            val avd = view.drawable as? AnimatedVectorDrawable
            // Removed when it ends: it starts at its first frame, which can come seconds after
            // this on a slow start. It is transparent by then, so a late removal shows nothing.
            avd?.registerAnimationCallback(object : Animatable2.AnimationCallback() {
                override fun onAnimationEnd(drawable: Drawable?) { endIntro() }
            })
            view.postDelayed({ endIntro() }, INTRO_MS + 8_000) // a safety net only
            avd?.start()
            // The tone, loaded now and started on the intro's first frame (when the animation
            // starts too), so they stay together however long the first frame takes.
            tone = runCatching { MediaPlayer.create(this, R.raw.intro_tone) }.getOrNull()?.apply {
                setOnCompletionListener { stopTone() }
            }
            view.doOnPreDraw {
                tone?.start()
                view.postDelayed({ appReady.value = true }, APP_AFTER_MS)
            }
        }
    }

    private fun stopTone() {
        val t = tone ?: return
        tone = null
        runCatching { if (t.isPlaying) t.stop() }
        t.release()
    }

    override fun onStop() {
        super.onStop()
        stopTone() // the app left during the intro: no sound in the background
    }

    private fun endIntro() {
        appReady.value = true
        // A key skips the picture and the sound together; the end of the animation leaves the
        // tone to finish its fade.
        if (intro != null && tone?.isPlaying == true && (intro?.drawable as? AnimatedVectorDrawable)?.isRunning == true) stopTone()
        val view = intro ?: return
        intro = null
        (view.drawable as? AnimatedVectorDrawable)?.stop()
        (view.parent as? ViewGroup)?.removeView(view)
    }
}
