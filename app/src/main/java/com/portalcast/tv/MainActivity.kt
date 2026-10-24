package com.portalcast.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.portalcast.tv.ui.intro.IntroScreen
import com.portalcast.tv.ui.nav.AppNavHost
import com.portalcast.tv.ui.theme.PortalcastTheme

/** The intro plays once per process: a cold start, not a return to the app. */
private object Intro {
    var shown = false
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PortalcastTheme {
                var showIntro by remember { mutableStateOf(!Intro.shown) }
                val finish = { Intro.shown = true; showIntro = false }
                // The app loads underneath while it plays; any key skips it (and is not passed on).
                Box(Modifier.fillMaxSize().onPreviewKeyEvent { if (showIntro) { finish(); true } else false }) {
                    AppNavHost(container = appContainer)
                    if (showIntro) IntroScreen(onDone = finish)
                }
            }
        }
    }
}
