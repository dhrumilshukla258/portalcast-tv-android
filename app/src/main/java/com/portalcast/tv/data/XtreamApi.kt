package com.portalcast.tv.data

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Mirrors the player_api.php action set implemented by portalcast-server's
 * Xtream emulation layer (src/routes/xtream/).
 */
interface XtreamApi {

    @GET("player_api.php")
    suspend fun login(
        @Query("username") username: String,
        @Query("password") password: String
    ): LoginResponse

    @GET("player_api.php")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories"
    ): List<Category>

    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
        @Query("category_id") categoryId: String? = null
    ): List<LiveStream>

    @GET("player_api.php")
    suspend fun getVodCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories"
    ): List<Category>

    @GET("player_api.php")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams",
        @Query("category_id") categoryId: String? = null
    ): List<VodStream>

    @GET("player_api.php")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_categories"
    ): List<Category>

    @GET("player_api.php")
    suspend fun getSeries(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series",
        @Query("category_id") categoryId: String? = null
    ): List<Series>

    companion object {
        fun liveStreamUrl(baseUrl: String, username: String, password: String, streamId: Int): String {
            val base = baseUrl.trimEnd('/')
            return "$base/live/$username/$password/$streamId.m3u8"
        }

        fun vodStreamUrl(baseUrl: String, username: String, password: String, streamId: Int, extension: String?): String {
            val base = baseUrl.trimEnd('/')
            val ext = extension?.takeIf { it.isNotBlank() } ?: "m3u8"
            return "$base/movie/$username/$password/$streamId.$ext"
        }
    }
}
