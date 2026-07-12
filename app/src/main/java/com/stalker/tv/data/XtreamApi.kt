package com.stalker.tv.data

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Mirrors the player_api.php action set implemented by stalker-m3u-server's
 * Xtream emulation layer (src/routes/xtream.ts).
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

    companion object {
        fun streamUrl(baseUrl: String, username: String, password: String, streamId: Int): String {
            val base = baseUrl.trimEnd('/')
            return "$base/live/$username/$password/$streamId.m3u8"
        }
    }
}
