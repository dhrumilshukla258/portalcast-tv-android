package com.portalcast.tv.data.net

import com.portalcast.tv.data.model.EpgData
import com.portalcast.tv.data.model.EpgProgramme
import com.squareup.moshi.JsonReader
import okio.BufferedSource

/**
 * Streaming reader for `GET /api/v2/epg`.
 *
 * The server returns the WHOLE guide in one response: on a real portal (10k channels) that
 * was 549k programmes, 50 MB of JSON. Building it as a JSON tree crashed the app with an
 * OutOfMemoryError on a 192 MB heap. This reads the body as a stream and keeps only what the
 * UI shows: per channel, up to [maxPerChannel] programmes overlapping now .. now + [aheadSec].
 *
 * Returns null when the server sends `[]` (cache cold/stale) or anything that isn't the
 * `{ timestamp, data: { channelId: [...] } }` object.
 */
object EpgStream {

    fun parse(
        source: BufferedSource,
        nowSec: Long,
        aheadSec: Long = 6 * 3600L,
        maxPerChannel: Int = 4,
    ): EpgData? {
        val reader = JsonReader.of(source)
        reader.isLenient = true
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var timestamp: String? = null
        val byChannel = HashMap<String, List<EpgProgramme>>()
        val windowEnd = nowSec + aheadSec

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "timestamp" -> timestamp = readScalar(reader)
                "data" -> {
                    if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) { reader.skipValue(); continue }
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val channelId = reader.nextName()
                        if (reader.peek() != JsonReader.Token.BEGIN_ARRAY) { reader.skipValue(); continue }
                        var kept: ArrayList<EpgProgramme>? = null
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val p = readProgramme(reader) ?: continue
                            val start = p.start_timestamp.toLongOrNull() ?: continue
                            val stop = p.stop_timestamp.toLongOrNull() ?: continue
                            if (stop <= nowSec || start >= windowEnd) continue
                            val list = kept ?: ArrayList<EpgProgramme>(maxPerChannel).also { kept = it }
                            list.add(p)
                        }
                        reader.endArray()
                        kept?.let { list ->
                            byChannel[channelId] = list.sortedBy { it.start_timestamp.toLongOrNull() ?: 0L }.take(maxPerChannel)
                        }
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return EpgData(timestamp = timestamp, data = byChannel)
    }

    /** One programme; only the fields the UI uses are kept (descriptions are skipped to save memory). */
    private fun readProgramme(reader: JsonReader): EpgProgramme? {
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) { reader.skipValue(); return null }
        var start: String? = null
        var stop: String? = null
        var name: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "start_timestamp" -> start = readScalar(reader)
                "stop_timestamp" -> stop = readScalar(reader)
                "name" -> name = readScalar(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (start == null || stop == null) return null
        return EpgProgramme(start_timestamp = start, stop_timestamp = stop, name = name)
    }

    /** A string or number as text; null for null; other shapes skipped. */
    private fun readScalar(reader: JsonReader): String? = when (reader.peek()) {
        JsonReader.Token.STRING, JsonReader.Token.NUMBER -> reader.nextString()
        JsonReader.Token.BOOLEAN -> reader.nextBoolean().toString()
        JsonReader.Token.NULL -> reader.nextNull<Any>().let { null }
        else -> { reader.skipValue(); null }
    }
}
