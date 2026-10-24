package com.portalcast.tv

import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.portalcast.tv.ui.nav.AppNavHost
import com.portalcast.tv.ui.theme.PortalcastTheme

/** The intro plays once per process: a cold start, not a return to the app. */
private object Intro {
    var shown = false
}

/** The intro's length (res/drawable/intro_logo.xml, tools/gen_intro_avd.py). */
private const val INTRO_MS = 1840L

class MainActivity : ComponentActivity() {
    /** The intro over the app while it plays; null once it is gone. */
    private var intro: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PortalcastTheme {
                // The app loads underneath while the intro plays; any key skips it (and is not passed on).
                Box(Modifier.fillMaxSize().onPreviewKeyEvent { if (intro != null) { endIntro(); true } else false }) {
                    AppNavHost(container = appContainer)
                }
            }
        }
        // Android 7.1+ only: older versions draw no gradients in a vector drawable (the P and
        // its arcs would be invisible) and run the animation on the main thread, where startup
        // work makes it jump. They open straight into the app.
        if (Build.VERSION.SDK_INT < 25) Intro.shown = true
        if (!Intro.shown) {
            Intro.shown = true
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
        }
    }

    private fun endIntro() {
        val view = intro ?: return
        intro = null
        (view.drawable as? AnimatedVectorDrawable)?.stop()
        (view.parent as? ViewGroup)?.removeView(view)
    }
}
