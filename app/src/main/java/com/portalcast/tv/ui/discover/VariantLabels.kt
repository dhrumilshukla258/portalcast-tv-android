package com.portalcast.tv.ui.discover

/** What a [VariantChip] says: a picture/format tag ("4K", "1080p") or a subtitle language. */
enum class VariantChipKind { Quality, Subtitles }

data class VariantChip(val text: String, val kind: VariantChipKind)

/** A variant label split for display: the language part large, the tags as chips. */
data class VariantLabelParts(val primary: String, val chips: List<VariantChip>)

/**
 * Splits the server's `variantLabel` (built in portalcast-server's discover variants
 * route as `"<language>[ (+ <lang> Subtitles)] <quality tags>"`, or bare quality tags,
 * or the raw catalog name) into a prominent primary part and chips:
 *
 * - `"Hindi (+ English Subtitles) 4K HDR"` → `Hindi` + [English subs, 4K, HDR]
 * - `"Original Audio (English Subtitles)"` → `Original Audio` + [English subs]
 * - `"4K HDR"` → `4K HDR`, no chips (nothing else to show large)
 *
 * The quality list mirrors the server's `QUALITY_TAGS` (content/titleClean.ts). Pure, JVM-tested.
 */
object VariantLabels {

    private val SUBTITLES = Regex("""\(\s*(?:\+\s*)?([^()]*?)\s+Subtitles\s*\)""", RegexOption.IGNORE_CASE)

    private val WHITESPACE = Regex("""\s+""")

    private val QUALITY_TOKENS = listOf(
        """Dual\s*Audio""", "Dubbed", "Dubb?", "Multi", "TriAudio", "4K", "UHD", "FHD", "HD", "SD", "SDR", "HDR",
        "HDRip", "HDTV", "HDCAM", "BluRay", "Blu-?Ray", "BRRip", "WEBRip", "WEB-?DL", "DVDRip", "DVD-?Rip",
        "CAM", "HDTS", "TS", "PDVD", "480p", "720p", "1080p", "2160p", "CC",
    ).sortedByDescending { it.length }

    private val TRAILING_QUALITY = Regex("""(?:^|\s)(${QUALITY_TOKENS.joinToString("|")})\s*$""", RegexOption.IGNORE_CASE)

    fun parse(label: String?): VariantLabelParts {
        var rest = label?.trim().orEmpty()
        val chips = mutableListOf<VariantChip>()

        val subs = SUBTITLES.find(rest)
        if (subs != null) {
            val lang = subs.groupValues[1].trim()
            if (lang.isNotEmpty()) chips += VariantChip("$lang subs", VariantChipKind.Subtitles)
            rest = (rest.substring(0, subs.range.first) + " " + rest.substring(subs.range.last + 1)).replace(WHITESPACE, " ").trim()
        }

        val quality = ArrayDeque<String>()
        while (true) {
            val m = TRAILING_QUALITY.find(rest) ?: break
            quality.addFirst(m.groupValues[1])
            rest = rest.substring(0, m.range.first).trim()
        }

        if (rest.isEmpty()) {
            // Only tags: show them large rather than an empty headline.
            return VariantLabelParts(quality.joinToString(" ").ifEmpty { "Version" }, chips)
        }
        return VariantLabelParts(rest, chips + quality.map { VariantChip(it, VariantChipKind.Quality) })
    }
}
