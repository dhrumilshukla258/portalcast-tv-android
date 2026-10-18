package com.portalcast.tv.util

import android.util.Base64
import com.portalcast.tv.data.net.AppJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Reads claims out of the server's HS256 JWTs without verifying them (client side only). */
object Jwt {
    fun payload(token: String): JsonObject? {
        val parts = token.split('.')
        if (parts.size < 2) return null
        return runCatching {
            val bytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            AppJson.parseToJsonElement(String(bytes, Charsets.UTF_8)) as? JsonObject
        }.getOrNull()
    }

    /** `exp` in unix seconds, or null if unreadable. */
    fun expiresAt(token: String): Long? = payload(token)?.get("exp")?.jsonPrimitive?.longOrNull

    fun expiresWithin(token: String, seconds: Long, nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
        val exp = expiresAt(token) ?: return false
        return exp - nowSeconds <= seconds
    }

    fun isExpired(token: String, nowSeconds: Long = System.currentTimeMillis() / 1000) = expiresWithin(token, 0, nowSeconds)
}
