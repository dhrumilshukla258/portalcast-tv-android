package com.portalcast.tv.di

import com.portalcast.tv.ui.components.IptvStatus
import java.io.File
import okhttp3.Cache
import android.app.Application
import com.portalcast.tv.BuildConfig
import com.portalcast.tv.data.net.ApiService
import com.portalcast.tv.data.net.AppJson
import com.portalcast.tv.data.net.AuthInterceptor
import com.portalcast.tv.data.net.DynamicBaseUrlInterceptor
import com.portalcast.tv.data.net.TokenAuthenticator
import com.portalcast.tv.data.net.TokenRefresher
import com.portalcast.tv.data.repo.AuthRepository
import com.portalcast.tv.data.repo.DefaultAuthRepository
import com.portalcast.tv.data.repo.DefaultDiscoverRepository
import com.portalcast.tv.data.repo.DefaultLiveRepository
import com.portalcast.tv.data.repo.DefaultSeriesRepository
import com.portalcast.tv.data.repo.DefaultUserRepository
import com.portalcast.tv.data.repo.DefaultVodRepository
import com.portalcast.tv.data.repo.DiscoverRepository
import com.portalcast.tv.data.repo.LiveRepository
import com.portalcast.tv.data.repo.SeriesRepository
import com.portalcast.tv.data.repo.UserRepository
import com.portalcast.tv.data.repo.VodRepository
import com.portalcast.tv.data.store.LocalPrefs
import com.portalcast.tv.data.store.SessionStore
import com.portalcast.tv.playback.DefaultProgressReporter
import com.portalcast.tv.playback.DefaultStreamResolver
import com.portalcast.tv.playback.ProgressReporter
import com.portalcast.tv.playback.StreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

/**
 * Manual dependency graph (plan decision #3). One instance per process, owned by
 * [com.portalcast.tv.PortalcastApp]. Order matters: stores → refresher → clients → api → repos.
 */
class AppContainer(app: Application) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionStore = SessionStore(app, appScope)
    val localPrefs = LocalPrefs(app)

    /** No interceptors: used by the refresher and by anything that must never trigger auth. */
    val bareClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val tokenRefresher = TokenRefresher(sessionStore, bareClient)

    /**
     * JSON client. Redirects are NOT followed so the cold-cache 302 on the list
     * routes is observable (contract §3). Logging is BASIC only: HEADERS would
     * print the Bearer token and BODY would print credentials.
     */
    val apiClient: OkHttpClient = bareClient.newBuilder()
        // Answers the server marks cacheable (ETag + max-age: categories, filters, details,
        // Discover rows) are kept on disk: reused without asking within max-age, then
        // revalidated (an unchanged answer is a bodyless 304), and kept across app restarts.
        .cache(Cache(File(app.cacheDir, "http-api"), 50L * 1024 * 1024))
        // The IPTV status rides on every network answer (X-IPTV): no polling while in use.
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            response.header("X-IPTV")?.let { IptvStatus.report(it) }
            response
        }
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(DynamicBaseUrlInterceptor(sessionStore))
        .addInterceptor(AuthInterceptor(sessionStore, tokenRefresher))
        .authenticator(TokenAuthenticator(tokenRefresher))
        .apply {
            if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        .build()

    /**
     * Client for media (ExoPlayer) and images (Coil): follows redirects (live `proxy=0`
     * returns a 302 to the CDN) and sends no Bearer (stream URLs carry their own token).
     */
    val mediaClient: OkHttpClient = bareClient.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", com.portalcast.tv.data.net.UserAgent.value).build())
        }
        .build()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(PLACEHOLDER_BASE_URL)
        .client(apiClient)
        .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create()

    val authRepository: AuthRepository = DefaultAuthRepository(api, sessionStore)
    val userRepository: UserRepository = DefaultUserRepository(api, sessionStore)
    val liveRepository: LiveRepository = DefaultLiveRepository(api, userRepository, sessionStore)
    val vodRepository: VodRepository = DefaultVodRepository(api, sessionStore)
    val seriesRepository: SeriesRepository = DefaultSeriesRepository(api, sessionStore)
    val discoverRepository: DiscoverRepository = DefaultDiscoverRepository(api, vodRepository, seriesRepository, sessionStore)

    // ---- playback (Phase 1c) ----------------------------------------------------

    /** movie-link resolution + per-session URL cache; live plays `channel.cmd`. */
    val streamResolver: StreamResolver = DefaultStreamResolver(api, sessionStore)

    /** Contract 6.2 progress rows: 30 s cadence, two rows per episode, never for live. */
    val progressReporter: ProgressReporter = DefaultProgressReporter(userRepository, sessionStore)

    companion object {
        /** Never actually contacted; [DynamicBaseUrlInterceptor] rewrites every request. */
        const val PLACEHOLDER_BASE_URL = "http://placeholder.invalid/"
    }
}
