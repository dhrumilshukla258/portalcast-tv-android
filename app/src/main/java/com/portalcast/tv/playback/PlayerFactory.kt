package com.portalcast.tv.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import okhttp3.OkHttpClient
import kotlin.math.min

/** Told about every HTTP load error the policy handles; may be called on a loader thread. */
fun interface LoadErrorListener {
    /** [retryDelayMs] is `C.TIME_UNSET` when the error is about to surface as fatal. */
    fun onHttpError(code: Int, errorCount: Int, retryDelayMs: Long)
}

/**
 * Builds the one ExoPlayer the player screen uses (plan decision #6).
 *
 * Network goes through [OkHttpDataSource] over the container's `mediaClient`, so
 * the User-Agent and redirect behaviour (live `proxy=0` 302s to the CDN) are the
 * client's, not ExoPlayer's, and no Bearer is ever attached to a stream request.
 * Never hand this factory the `apiClient`.
 */
@OptIn(UnstableApi::class)
class PlayerFactory(
    private val context: Context,
    private val mediaClient: OkHttpClient,
    private val userAgent: String,
) {
    fun create(listener: LoadErrorListener): ExoPlayer {
        val http = OkHttpDataSource.Factory(mediaClient).setUserAgent(userAgent)
        val dataSource = DefaultDataSource.Factory(context, http)
        val mediaSources = DefaultMediaSourceFactory(dataSource)
            .setLoadErrorHandlingPolicy(PortalcastLoadErrorPolicy(listener))
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSources)
            .setLoadControl(DefaultLoadControl())
            .setHandleAudioBecomingNoisy(false)
            .build()
    }

    /** HLS gets an explicit mime type (the proxy URLs have no extension); progressive is sniffed. */
    fun mediaItem(stream: ResolvedStream): MediaItem = MediaItem.Builder()
        .setUri(stream.url)
        .apply { if (stream.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
        .build()

    companion object {
        /** The HTTP status buried in a player/loader exception chain, or null when it was not an HTTP failure. */
        fun httpStatusOf(t: Throwable?): Int? =
            generateSequence(t) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                .firstOrNull()?.responseCode
    }
}

/**
 * Contract §5 retry rules on top of ExoPlayer's defaults:
 *
 * - 401/403: no retry at the data-source level. The error becomes fatal immediately
 *   and reaches `Player.Listener.onPlayerError`; the screen re-resolves the stream
 *   once (new token) and re-prepares. Retrying blindly would hammer `/player/` with
 *   a dead token.
 * - 500: same back-off but only [SERVER_ERROR_RETRIES] attempts (portal link failure).
 * - 429 and 404/502/503: exponential back-off 1 s, 2 s, 4 s, capped at 5 s, at
 *   most [MAX_RETRIES] attempts, so a shared rate-limit bucket or a player that fell
 *   behind the live edge recovers without a tight loop.
 * - anything else: ExoPlayer's default behaviour.
 */
@OptIn(UnstableApi::class)
class PortalcastLoadErrorPolicy(private val listener: LoadErrorListener) : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val code = PlayerFactory.httpStatusOf(loadErrorInfo.exception)
            ?: return super.getRetryDelayMsFor(loadErrorInfo)
        val delay = when (code) {
            401, 403 -> C.TIME_UNSET
            // 500 is the server saying it could not get a link from the portal ("Failed to
            // generate URL"); retrying 10 times meant ~45 s of "Loading…" before any message.
            500 -> if (loadErrorInfo.errorCount > SERVER_ERROR_RETRIES) C.TIME_UNSET else backoffMs(loadErrorInfo.errorCount)
            429, 404, 502, 503 -> backoffMs(loadErrorInfo.errorCount)
            else -> super.getRetryDelayMsFor(loadErrorInfo)
        }
        listener.onHttpError(code, loadErrorInfo.errorCount, delay)
        return delay
    }

    /** Raised so the loader does not surface a non-fatal error to the renderers before our retries are spent. */
    override fun getMinimumLoadableRetryCount(dataType: Int): Int = MAX_RETRIES

    companion object {
        const val MAX_RETRIES = 10
        const val SERVER_ERROR_RETRIES = 3
        const val MAX_DELAY_MS = 5_000L

        /** 1 s, 2 s, 4 s, then 5 s; `C.TIME_UNSET` once [MAX_RETRIES] attempts have failed. */
        fun backoffMs(errorCount: Int): Long {
            if (errorCount > MAX_RETRIES) return C.TIME_UNSET
            val exp = (errorCount - 1).coerceIn(0, 12)
            return min(1_000L shl exp, MAX_DELAY_MS)
        }
    }
}
