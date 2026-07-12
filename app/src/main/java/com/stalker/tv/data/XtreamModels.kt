package com.stalker.tv.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserInfo(
    @Json(name = "auth") val auth: Int?,
    @Json(name = "username") val username: String?,
    @Json(name = "status") val status: String?
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "user_info") val userInfo: UserInfo?
)

@JsonClass(generateAdapter = true)
data class Category(
    @Json(name = "category_id") val categoryId: String,
    @Json(name = "category_name") val categoryName: String
)

@JsonClass(generateAdapter = true)
data class LiveStream(
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "name") val name: String,
    @Json(name = "stream_icon") val streamIcon: String?,
    @Json(name = "category_id") val categoryId: String?
)
