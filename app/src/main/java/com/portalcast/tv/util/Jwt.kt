package com.portalcast.tv.util

import com.portalcast.tv.data.net.AppJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.ByteArrayOutputStream

/**
 * Reads claims out of the server's HS256 JWTs without verifying them (client side only).
 * Uses its own base64url decoder: `java.util.Base64` needs API 26 and `android.util.Base64`
 * is a no-op stub in JVM unit tests, which would silently disable proactive refresh there.
 */
object Jwt {
    fun payload(token: String): JsonObject? {
        val parts = token.split('.')
        if (parts.size < 2) return null
        return runCatching {
            AppJson.parseToJsonElement(String(decodeBase64Url(parts[1]), Charsets.UTF_8)) as? JsonObject
        }.getOrNull()
    }

    /** `exp` in unix seconds, or null if unreadable. */
    fun expiresAt(token: String): Long? = payload(token)?.get("exp")?.jsonPrimitive?.longOrNull

    fun expiresWithin(token: String, seconds: Long, nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
        val exp = expiresAt(token) ?: return false
        return exp - nowSeconds <= seconds
    }

    fun isExpired(token: String, nowSeconds: Long = System.currentTimeMillis() / 1000) = expiresWithin(token, 0, nowSeconds)

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** Decodes base64 or base64url, padded or not. Throws on characters outside the alphabet. */
    internal fun decodeBase64Url(input: String): ByteArray {
        val out = ByteArrayOutputStream(input.length * 3 / 4 + 1)
        var buffer = 0
        var bits = 0
        for (raw in input) {
            val ch = when (raw) { '-' -> '+'; '_' -> '/'; else -> raw }
            if (ch == '=' || ch.isWhitespace()) continue
            val value = ALPHABET.indexOf(ch)
            require(value >= 0) { "Not base64" }
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer ushr bits) and 0xFF)
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return out.toByteArray()
    }
}
