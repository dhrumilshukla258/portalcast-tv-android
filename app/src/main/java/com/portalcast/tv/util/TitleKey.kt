package com.portalcast.tv.util

/**
 * Kotlin port of portalcast-server `src/content/titleClean.ts` (stripReleaseNoise /
 * normalizeTitleKey): the same rules the server uses to group a title's language and
 * quality versions into one Discover entry ("ABC Hindi 4K", "ABC - Tamil" -> "abc").
 * Keep in sync with that file; it is the source of truth.
 */
object TitleKey {

    private val LANGUAGE_ALIASES: Map<String, List<String>> = linkedMapOf(
        "Hindi" to listOf("hindi", "hin", "south dub", "south dubbed", "eng dub", "english dub"),
        "Tamil" to listOf("tamil", "tam"),
        "Telugu" to listOf("telugu", "tel"),
        "Malayalam" to listOf("malayalam", "mal"),
        "Kannada" to listOf("kannada", "kan", "kann"),
        "Bengali" to listOf("bengali", "bangla", "ben", "bangali"),
        "Punjabi" to listOf("punjabi", "pun"),
        "Marathi" to listOf("marathi", "mar"),
        "Odia" to listOf("odia", "oriya"),
        "Gujarati" to listOf("gujarati", "gujrati", "guj"),
        "Assamese" to listOf("assamese", "asm"),
        "Urdu" to listOf("urdu"),
        "Bhojpuri" to listOf("bhojpuri", "bhoj"),
        "Sindhi" to listOf("sindhi"),
        "English" to listOf("english", "eng", "en"),
        "French" to listOf("french"),
        "Spanish" to listOf("spanish", "es"),
        "German" to listOf("german"),
        "Italian" to listOf("italian"),
        "Portuguese" to listOf("portuguese", "porteguese", "pt"),
        "Russian" to listOf("russian"),
        "Arabic" to listOf("arabic"),
        "Chinese" to listOf("chinese"),
        "Japanese" to listOf("japanese"),
        "Korean" to listOf("korean"),
    )

    private val QUALITY_TAGS = listOf(
        "Dual\\s*Audio", "Dubbed", "Dubb?", "Multi", "TriAudio", "4K", "UHD", "FHD", "HD", "SD", "SDR", "HDR",
        "HDRip", "HDTV", "HDCAM", "BluRay", "Blu-?Ray", "BRRip", "WEBRip", "WEB-?DL", "DVDRip", "DVD-?Rip",
        "CAM", "HDTS", "TS", "PDVD", "480p", "720p", "1080p", "2160p", "CC",
        "Natok", "Notok", "Web\\s*Series",
    )

    private val ALIAS_TO_CANONICAL: Map<String, String> = buildMap {
        for ((canonical, aliases) in LANGUAGE_ALIASES) for (a in aliases) put(a.lowercase(), canonical)
    }

    private val LANGUAGE_ALTERNATION = ALIAS_TO_CANONICAL.keys.sortedByDescending { it.length }.joinToString("|")
    private val LANGUAGE_SUB_PATTERN = "(?:$LANGUAGE_ALTERNATION)\\s+Sub(?:bed|titled)?"

    private val VARIANT_RE = Regex(
        "\\b(" + (ALIAS_TO_CANONICAL.keys + QUALITY_TAGS + LANGUAGE_SUB_PATTERN).sortedByDescending { it.length }.joinToString("|") + ")\\b",
        RegexOption.IGNORE_CASE,
    )
    private val LANGUAGE_RE = Regex("\\b($LANGUAGE_ALTERNATION)\\b", RegexOption.IGNORE_CASE)
    private val QUALITY_RE = Regex("\\b(" + QUALITY_TAGS.sortedByDescending { it.length }.joinToString("|") + ")\\b", RegexOption.IGNORE_CASE)

    private val CHANNEL_PREFIX_RE = Regex(
        "^(Colors(?:\\s+(?:Kannada|Tamil|Gujarati|Bangla|Marathi|Odia|Punjabi|Rishtey|Super|Infinity))?|Zee(?:\\s+(?:TV|Telugu|Tamil|Kannada|Marathi|Bangla|Punjabi|Malayalam|Cafe|Cinema|News|Anmol|Bollywood|Classic|Action|World))?|Star(?:\\s+(?:Plus|Vijay|Jalsha|Pravah|Suvarna|Maa|Bharat|Utsav|Gold|World|Movies|Sports))?|Sony(?:\\s+(?:TV|SAB|Liv|Max|Aath|Rox|Marathi))?|Sun(?:\\s+(?:TV|Bangla|Marathi|Neo|Life))?|Gemini(?:\\s+(?:TV|Music|Movies))?|Maa(?:\\s+(?:TV|Gold|Movies))?|ETV(?:\\s+(?:Telugu|Plus|Andhra))?|Life\\s+OK|Asianet|Vijay\\s*TV|SAB\\s*TV|Rishtey|Big\\s+Magic|Aaj\\s+Tak|Republic\\s*TV|NDTV|CNN|BBC|Discovery|National\\s+Geographic|Nat\\s+Geo|HUM\\s*TV|ARY\\s*Digital|Har\\s*Pal\\s*Geo|MUN\\s*TV|Dangal\\s*TV|And\\s*TV|MTV|Express\\s*Tv|Urdu\\s*Tv\\s*Show|Urdu\\s*1|Untold(?:\\s+UK)?)\\b\\s*[:\\-]?\\s*",
        RegexOption.IGNORE_CASE,
    )

    /** Display title without channel prefixes and language/quality tags (case preserved). */
    fun stripReleaseNoise(name: String): String = name
        .replace(CHANNEL_PREFIX_RE, "")
        .replace(VARIANT_RE, "")
        .replace(Regex("[\\[(]\\s*[\\])]"), " ")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("^[\\s\\-:|,]+|[\\s\\-:|,]+$"), "")
        .trim()

    /** Grouping key: same as the server's `normalizeTitleKey`. */
    fun normalize(name: String): String {
        val stripped = stripReleaseNoise(name).lowercase()
        val collapsed = stripped.replace(Regex("\\s*:\\s*"), " ").replace(Regex("\\s+"), " ").trim()
        return collapsed.ifEmpty { name.trim().lowercase() }
    }

    /**
     * Short label for one version, e.g. "Hindi · 4K" or "Tamil", from the tags in its raw
     * name. Falls back to the raw name when it carries no recognisable tags.
     */
    fun variantLabel(name: String): String {
        val languages = LANGUAGE_RE.findAll(name).mapNotNull { ALIAS_TO_CANONICAL[it.value.lowercase()] }.distinct().toList()
        // Quality tags from what's left after removing language phrases, so "South Dub" counts
        // once as Hindi instead of also yielding a stray "Dub".
        val rest = LANGUAGE_RE.replace(name, " ")
        val qualities = QUALITY_RE.findAll(rest).map { it.value.trim() }.distinctBy { it.uppercase() }.toList()
        val parts = languages + qualities
        return if (parts.isEmpty()) name.trim() else parts.joinToString(" · ")
    }
}
