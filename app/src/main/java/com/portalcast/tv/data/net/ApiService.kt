package com.portalcast.tv.data.net

import com.portalcast.tv.data.model.AuthResponse
import com.portalcast.tv.data.model.ChannelGroup
import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.DeviceCodeResponse
import com.portalcast.tv.data.model.DevicePollRequest
import com.portalcast.tv.data.model.DevicePollResponse
import com.portalcast.tv.data.model.DiscoverBrowseResponse
import com.portalcast.tv.data.model.DiscoverFacets
import com.portalcast.tv.data.model.ExpiryResponse
import com.portalcast.tv.data.model.LoginRequest
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.Paged
import com.portalcast.tv.data.model.PreferencesResponse
import com.portalcast.tv.data.model.ProfileResponse
import com.portalcast.tv.data.model.ProgressRecord
import com.portalcast.tv.data.model.ProviderKeyResponse
import com.portalcast.tv.data.model.RecommendationsResponse
import com.portalcast.tv.data.model.RefreshRequest
import com.portalcast.tv.data.model.RefreshResponse
import com.portalcast.tv.data.model.SuccessResponse
import com.portalcast.tv.data.model.VariantsResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import okhttp3.ResponseBody

/**
 * Every server route the app calls, one function per route, paths relative to the
 * user's server origin (the base URL is rewritten per request by
 * [DynamicBaseUrlInterceptor]). Field-level behaviour: docs/api-contract.md.
 *
 * Null `@Query` values are omitted by Retrofit. Non-2xx responses throw
 * `retrofit2.HttpException`; map them with [ApiError.from].
 */
interface ApiService {

    // ---- auth (no Bearer; the interceptor skips /api/auth/) ----------------

    /** Report the decoded video quality of a title (servers from 2026-10-19; older ones 404). */
    @POST("api/v2/media-info")
    suspend fun reportMediaInfo(@Body body: com.portalcast.tv.data.model.MediaInfoReport): okhttp3.ResponseBody

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): RefreshResponse

    /** No body. Not rate limited. */
    @POST("api/auth/device/code")
    suspend fun deviceCode(): DeviceCodeResponse

    /** Poll every 4 s, one in flight. The row is deleted on the first `authorized` reply. */
    @POST("api/auth/device/poll")
    suspend fun devicePoll(@Body body: DevicePollRequest): DevicePollResponse

    // ---- user ------------------------------------------------------------------

    @GET("api/user/profile")
    suspend fun profile(): ProfileResponse

    /** Partial object; server shallow-merges allowlisted top-level keys. */
    @PUT("api/user/preferences")
    suspend fun updatePreferences(@Body patch: JsonObject): PreferencesResponse

    @GET("api/user/progress")
    suspend fun progress(): List<ProgressRecord>

    /** PUT, not POST (the server docs are wrong). */
    @PUT("api/user/progress")
    suspend fun saveProgress(@Body record: ProgressRecord): SuccessResponse

    @DELETE("api/user/progress/{mediaId}")
    suspend fun deleteProgress(@Path("mediaId") mediaId: String): SuccessResponse

    // ---- misc ------------------------------------------------------------------

    /** 404 on servers older than 2026-09-21; tolerate it. Never call /api/config. */
    @GET("api/v2/provider-key")
    suspend fun providerKey(): ProviderKeyResponse

    @GET("api/v2/expiry")
    suspend fun expiry(): ExpiryResponse

    // ---- live ------------------------------------------------------------------

    /** Bare array. 302→403 for non-admins when the server cache is cold. */
    @GET("api/v2/groups")
    suspend fun liveGroups(): List<ChannelGroup>

    /** Bare array, whole list, sorted by name. `cmd` is the playable HLS URL. */
    @GET("api/v2/channels")
    suspend fun channels(): List<Channel>

    /**
     * The whole guide, often tens of MB: never parse it as a tree. Read it with
     * [EpgStream.parse] (keeps only what the UI shows). `@Streaming` stops OkHttp/Retrofit
     * from buffering the body in memory.
     */
    @Streaming
    @GET("api/v2/epg")
    suspend fun epg(): ResponseBody

    /**
     * Only programmes overlapping the current hour + [hours] (server precomputes it once per
     * hour: ~1.6 MB gzipped vs ~6.8 MB for the whole guide on a 10k-channel portal). Same body
     * shape as [epg] plus windowStart/windowEnd. 404 on servers older than 2026-10-19.
     */
    @Streaming
    @GET("api/v2/epg/window")
    suspend fun epgWindow(@Query("hours") hours: Int = 6): ResponseBody

    // ---- movies ----------------------------------------------------------------

    @GET("api/v2/movie-groups")
    suspend fun movieGroups(): Paged<ChannelGroup>

    /**
     * List: `category` + `page` (+ `search`, `sort`). Detail/file: `movieId` + `category`.
     * Episode file: `movieId=<seriesId>&seasonId=&episodeId=&category=*`.
     * Always pass `category` (use "*" when unknown) or you hit a redirect.
     */
    @GET("api/v2/movies")
    suspend fun movies(
        @Query("category") category: String?,
        @Query("page") page: Int? = null,
        @Query("search") search: String? = null,
        @Query("sort") sort: String? = null,
        @Query("movieId") movieId: String? = null,
        @Query("seasonId") seasonId: String? = null,
        @Query("episodeId") episodeId: String? = null,
        @Query("pageAtaTime") pageAtATime: Int = 1,
    ): Paged<MediaItem>

    /**
     * Returns `{ js: { cmd: "/api/proxy?t=…[&m3u8=1]" } }`. `cmd` is relative to the
     * server origin. `title`/`category` label the admin "who's watching" view.
     * `series` = season number for episodes, omit for movies.
     */
    @GET("api/v2/movie-link")
    suspend fun movieLink(
        @Query("id") fileId: String,
        @Query("series") series: String? = null,
        @Query("title") title: String? = null,
        @Query("category") category: String? = null,
        /** Browsed item (movie list id / episode id) and its type, for the server's broken-stream tracking. Ignored by older servers. */
        @Query("itemId") itemId: String? = null,
        @Query("itemType") itemType: String? = null,
    ): JsonObject

    // ---- series ----------------------------------------------------------------

    @GET("api/v2/series-groups")
    suspend fun seriesGroups(): Paged<ChannelGroup>

    /**
     * List: `category` + `page`. Seasons: `movieId=<seriesId>` (data[0] is a season).
     * Episodes: `movieId` + `seasonId` + `page`. Always pass `category` ("*" fallback).
     */
    @GET("api/v2/series")
    suspend fun series(
        @Query("category") category: String?,
        @Query("page") page: Int? = null,
        @Query("search") search: String? = null,
        @Query("sort") sort: String? = null,
        @Query("movieId") movieId: String? = null,
        @Query("seasonId") seasonId: String? = null,
        @Query("pageAtaTime") pageAtATime: Int = 1,
    ): Paged<MediaItem>

    // ---- discover (page size 40) ------------------------------------------------

    @GET("api/v2/discover/facets")
    suspend fun discoverFacets(@Query("type") type: String?): DiscoverFacets

    @GET("api/v2/discover/browse")
    suspend fun discoverBrowse(
        @Query("type") type: String?,
        @Query("genre") genre: String? = null,
        @Query("country") country: String? = null,
        @Query("language") language: String? = null,
        @Query("theme") theme: String? = null,
        @Query("page") page: Int = 1,
    ): DiscoverBrowseResponse

    @GET("api/v2/discover/recommendations")
    suspend fun discoverRecommendations(@Query("type") type: String?): RecommendationsResponse

    @GET("api/v2/discover/variants")
    suspend fun discoverVariants(@Query("id") prefixedId: String): VariantsResponse
}
