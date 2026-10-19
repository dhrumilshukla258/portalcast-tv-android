package com.portalcast.tv.ui.player

import java.util.Locale

/** Display strings for the decoded video. Pure, JVM-tested. */
object VideoStats {

    /** Same classes as the server's qualityLabel (content/mediaInfo.ts); letterboxed 1920x800 is 1080p. */
    fun label(width: Int, height: Int): String {
        val h = maxOf(height, Math.round(width * 9 / 16.0).toInt())
        return when {
            h >= 2000 -> "2160p"
            h >= 1300 -> "1440p"
            h >= 1000 -> "1080p"
            h >= 700 -> "720p"
            h >= 560 -> "576p"
            h >= 460 -> "480p"
            else -> "SD"
        }
    }

    /** "H.264", "HEVC", "AV1", "VP9", "MPEG-2"; null when unknown. */
    fun codecName(mime: String?, codecs: String?): String? {
        val c = (codecs ?: "").lowercase(Locale.US)
        val m = (mime ?: "").lowercase(Locale.US)
        return when {
            c.startsWith("avc") || m.endsWith("/avc") -> "H.264"
            c.startsWith("hvc") || c.startsWith("hev") || m.endsWith("/hevc") -> "HEVC"
            c.startsWith("av01") || m.endsWith("/av01") -> "AV1"
            c.startsWith("vp09") || m.endsWith("x-vnd.on2.vp9") -> "VP9"
            m.endsWith("/mpeg2") -> "MPEG-2"
            else -> null
        }
    }

    /** "fps" shown with up to 3 decimals, trimmed: 23.976, 25, 29.97, 59.94. */
    fun fps(fps: Double): String =
        String.format(Locale.US, "%.3f", fps).trimEnd('0').trimEnd('.')

    /** "1080p · 23.976 fps · H.264 · 1920×1080". */
    fun describe(width: Int, height: Int, fps: Double?, codec: String?): String =
        listOfNotNull(label(width, height), fps?.let { "${fps(it)} fps" }, codec, "$width×$height").joinToString(" · ")
}
