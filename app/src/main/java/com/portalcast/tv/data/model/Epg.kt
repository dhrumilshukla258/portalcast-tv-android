package com.portalcast.tv.data.model

import com.portalcast.tv.data.net.AppJson
import com.portalcast.tv.data.net.LenientStr
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** One programme. Timestamps are unix **seconds** as strings. */
@Serializable
data class EpgProgramme(
    val start_timestamp: LenientStr = "0",
    val stop_timestamp: LenientStr = "0",
    val name: String? = null,
    val description: String? = null,
) {
    val startMs: Long get() = (start_timestamp.toLongOrNull() ?: 0L) * 1000
    val stopMs: Long get() = (stop_timestamp.toLongOrNull() ?: 0L) * 1000
    fun isOnAt(nowMs: Long) = nowMs in startMs until stopMs
    fun progressAt(nowMs: Long): Float {
        val len = stopMs - startMs
        if (len <= 0) return 0f
        return ((nowMs - startMs).toFloat() / len).coerceIn(0f, 1f)
    }
}

/**
 * `GET /api/v2/epg` body: `{ timestamp, data: { [channelId]: EpgProgramme[] } }`,
 * or the bare array `[]` when the server cache is cold/stale (then [parse] returns null).
 */
@Serializable
data class EpgData(
    val timestamp: LenientStr? = null,
    val data: Map<String, List<EpgProgramme>> = emptyMap(),
) {
    fun forChannel(channelId: String): List<EpgProgramme> = data[channelId].orEmpty()

    fun nowNext(channelId: String, nowMs: Long = System.currentTimeMillis()): Pair<EpgProgramme?, EpgProgramme?> {
        val list = forChannel(channelId).sortedBy { it.startMs }
        val idx = list.indexOfFirst { it.isOnAt(nowMs) }
        if (idx < 0) {
            val next = list.firstOrNull { it.startMs > nowMs }
            return null to next
        }
        return list[idx] to list.getOrNull(idx + 1)
    }

    companion object {
        fun parse(element: JsonElement): EpgData? =
            if (element is JsonObject) runCatching { AppJson.decodeFromJsonElement(serializer(), element) }.getOrNull() else null
    }
}
