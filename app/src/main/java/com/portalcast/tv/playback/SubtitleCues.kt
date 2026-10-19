package com.portalcast.tv.playback

/** One timed subtitle line (text may contain line breaks). */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

/**
 * Parses WebVTT (what the server's subtitle download returns) and SRT into sorted cues,
 * and finds the cue on screen for a playback position plus a user delay. We render
 * downloaded subtitles ourselves so the delay can move them earlier as well as later
 * (ExoPlayer has no subtitle offset). Pure, JVM-tested.
 */
object SubtitleCues {

    private val TIME = Regex("""(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[.,](\d{1,3})""")
    private val ARROW = Regex("""-->""")
    private val WHITESPACE = Regex("""\s+""")
    private val TAGS = Regex("""<[^>]+>|\{\[^}]*\}""")

    fun parse(text: String): List<SubtitleCue> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = ArrayList<SubtitleCue>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!ARROW.containsMatchIn(line)) { i++; continue }
            val parts = line.split("-->")
            val start = parts.getOrNull(0)?.let { toMs(it) }
            val end = parts.getOrNull(1)?.let { toMs(it.trim().split(WHITESPACE).first()) }
            i++
            val body = StringBuilder()
            while (i < lines.size && lines[i].isNotBlank()) {
                if (body.isNotEmpty()) body.append('\n')
                body.append(lines[i].replace(TAGS, "").trim())
                i++
            }
            if (start != null && end != null && end > start && body.isNotBlank()) out += SubtitleCue(start, end, body.toString().trim())
        }
        out.sortBy { it.startMs }
        return out
    }

    private fun toMs(s: String): Long? {
        val m = TIME.find(s.trim()) ?: return null
        val h = m.groupValues[1].toLongOrNull() ?: 0L
        val min = m.groupValues[2].toLong()
        val sec = m.groupValues[3].toLong()
        val frac = m.groupValues[4].padEnd(3, '0').take(3).toLong()
        return ((h * 60 + min) * 60 + sec) * 1000 + frac
    }

    /**
     * The cue showing at [positionMs] with [delayMs] applied (positive = subtitles later,
     * negative = earlier). Binary search: O(log n) per frame.
     */
    fun at(cues: List<SubtitleCue>, positionMs: Long, delayMs: Long): SubtitleCue? {
        if (cues.isEmpty()) return null
        val t = positionMs - delayMs
        var lo = 0
        var hi = cues.size - 1
        var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (cues[mid].startMs <= t) { best = mid; lo = mid + 1 } else hi = mid - 1
        }
        // Overlapping cues: the latest-starting one that still covers t.
        var k = best
        while (k >= 0 && k > best - 4) {
            val c = cues[k]
            if (t < c.endMs) return c
            k--
        }
        return null
    }
}
