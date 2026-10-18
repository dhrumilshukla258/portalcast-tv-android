package com.portalcast.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.portalcast.tv.ui.nav.AppNavHost
import com.portalcast.tv.ui.theme.PortalcastTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PortalcastTheme {
                AppNavHost(container = appContainer)
            }
        }
    }
}
