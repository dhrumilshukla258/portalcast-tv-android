package com.portalcast.tv

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
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
        // 500 MB of images on disk (Coil's default is 2% of the disk, much less on a TV):
        // posters seen once stay for weeks of browsing (Phase 4).
        .diskCache {
            DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(500L * 1024 * 1024).build()
        }
        .build()
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PortalcastApp).container
