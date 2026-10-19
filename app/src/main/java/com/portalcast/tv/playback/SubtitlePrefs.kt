package com.portalcast.tv.playback

import android.content.Context

/**
 * Per-title subtitle choices kept on the device: the timing offset the viewer set and
 * the online subtitle they picked (so the next play starts in sync), plus the preferred
 * search language. Keys are "movie:<id>" / "episode:<id>".
 */
class SubtitlePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("subtitles", Context.MODE_PRIVATE)

    fun delayMs(key: String): Long = prefs.getLong("delay:$key", 0L)
    fun setDelayMs(key: String, ms: Long) = prefs.edit().apply { if (ms == 0L) remove("delay:$key") else putLong("delay:$key", ms) }.apply()

    /** Online subtitle chosen for this title ("os:<n>" | "subdl:<path>"), or null. */
    fun subtitleId(key: String): String? = prefs.getString("sub:$key", null)
    fun setSubtitleId(key: String, id: String?) = prefs.edit().apply { if (id == null) remove("sub:$key") else putString("sub:$key", id) }.apply()

    var language: String
        get() = prefs.getString("language", "en") ?: "en"
        set(value) = prefs.edit().putString("language", value).apply()

    companion object {
        /** Common subtitle languages offered in the picker (ISO 639-1). */
        val LANGUAGES = listOf(
            "en" to "English", "hi" to "Hindi", "es" to "Spanish", "fr" to "French", "de" to "German",
            "ar" to "Arabic", "pt" to "Portuguese", "ta" to "Tamil", "te" to "Telugu", "ur" to "Urdu",
        )
    }
}
