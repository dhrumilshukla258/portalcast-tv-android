package com.portalcast.tv.playback

import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.net.ApiService
import com.portalcast.tv.data.net.apiCall
import com.portalcast.tv.data.net.primitiveOrNull
import com.portalcast.tv.data.store.SessionStore
import com.portalcast.tv.util.ImageUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * [StreamResolver] over `/api/v2/movie-link` (contract §3.4, §5).
 *
 * - Live: `channel.cmd` as-is (already absolute and tokenized); HLS.
 * - Movie / Episode: `movie-link?id=<fileId>&series=<season no>&title=<label>&category=<label category>`,
 *   `js.cmd` (fallback top-level `cmd`) prefixed with the server origin;
 *   HLS when the cmd carries `m3u8`.
 *
 * The last URL per session is cached so a re-prepare (next episode and back, a
 * rotation) does not mint a new stream token; `forceRefresh` bypasses the cache
 * and is what the player uses after a 401/403 from the stream.
 */
class DefaultStreamResolver(
    private val api: ApiService,
    private val serverUrl: () -> String?,
) : StreamResolver {

    constructor(api: ApiService, sessionStore: SessionStore) : this(api, { sessionStore.serverUrl.value })

    private val cache = ConcurrentHashMap<String, ResolvedStream>()

    override suspend fun resolve(session: PlaybackSession, forceRefresh: Boolean): Result<ResolvedStream> = when (session) {
        is PlaybackSession.Live -> resolveLive(session)
        is PlaybackSession.Movie -> resolveVod(
            key = "movie:${session.fileId}",
            fileId = session.fileId,
            series = null,
            title = session.label,
            category = session.item.labelCategory ?: session.category,
            forceRefresh = forceRefresh,
        )
        is PlaybackSession.Episode -> resolveVod(
            key = "episode:${session.fileId}",
            fileId = session.fileId,
            series = session.episode.seriesNumber?.toString(),
            title = session.label,
            category = session.series.labelCategory ?: session.episode.labelCategory ?: session.category,
            forceRefresh = forceRefresh,
        )
    }

    private fun resolveLive(session: PlaybackSession.Live): Result<ResolvedStream> {
        val cmd = session.channel.cmd?.trim()?.takeIf { it.isNotEmpty() }
            ?: return Result.failure(ApiError.Malformed("Channel has no stream URL"))
        val url = ImageUrl.absolute(serverUrl(), cmd) ?: return Result.failure(ApiError.NoServer())
        return Result.success(ResolvedStream(url, isHls = true))
    }

    private suspend fun resolveVod(
        key: String,
        fileId: String,
        series: String?,
        title: String?,
        category: String?,
        forceRefresh: Boolean,
    ): Result<ResolvedStream> {
        if (!forceRefresh) cache[key]?.let { return Result.success(it) }
        val origin = serverUrl() ?: return Result.failure(ApiError.NoServer())
        return apiCall(PATH) {
            val body = api.movieLink(
                fileId = fileId,
                series = series,
                title = title?.takeIf { it.isNotBlank() },
                category = category?.takeIf { it.isNotBlank() },
            )
            val js = body["js"] as? kotlinx.serialization.json.JsonObject
            val cmd = (js?.get("cmd").primitiveOrNull() ?: body["cmd"].primitiveOrNull())?.trim()
            if (cmd.isNullOrEmpty()) throw ApiError.Malformed("Stream URL not found")
            val url = ImageUrl.absolute(origin, cmd) ?: throw ApiError.NoServer()
            ResolvedStream(url, isHls = isHlsUrl(cmd)).also { cache[key] = it }
        }
    }

    /** Drop every cached URL (sign-out, server change). */
    fun clear() = cache.clear()

    companion object {
        private const val PATH = "/api/v2/movie-link"

        /** `&m3u8=1` on the proxy cmd, or a playlist path, means HLS; anything else is progressive. */
        fun isHlsUrl(cmdOrUrl: String): Boolean = cmdOrUrl.contains("m3u8", ignoreCase = true)
    }
}
