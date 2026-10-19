package com.portalcast.tv

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.portalcast.tv.di.AppContainer

class PortalcastApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** Coil uses the media client: no Bearer, redirects followed, our User-Agent. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { container.mediaClient }
        .crossfade(false) // a fade per poster while scrolling cost frames on TV hardware
        .build()
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PortalcastApp).container
