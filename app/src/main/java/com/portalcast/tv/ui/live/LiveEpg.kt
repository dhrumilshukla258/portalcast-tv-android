package com.portalcast.tv.ui.live

import com.portalcast.tv.data.model.EpgData
import com.portalcast.tv.data.model.EpgProgramme
import java.util.Calendar
import java.util.TimeZone

/**
 * EPG lookups and strings for the Live list. Pure, JVM-tested. The screen never sorts:
 * [index] sorts every channel's programmes once (off the main thread, in the ViewModel),
 * and the lookups below assume that order.
 */
object LiveEpg {

    /** Channel id to its programmes sorted by start time (channels without programmes are left out). */
    fun index(epg: EpgData): Map<String, List<EpgProgramme>> {
        val out = HashMap<String, List<EpgProgramme>>(epg.data.size * 2)
        for ((id, list) in epg.data) {
            if (list.isNotEmpty()) out[id] = list.sortedBy { it.startMs }
        }
        return out
    }

    /** Programme on at [nowMs] (or null) and the one after it (or the next upcoming one). [sorted] by start. */
    fun nowNext(sorted: List<EpgProgramme>, nowMs: Long): Pair<EpgProgramme?, EpgProgramme?> {
        val idx = sorted.indexOfFirst { it.isOnAt(nowMs) }
        if (idx < 0) return null to sorted.firstOrNull { it.startMs > nowMs }
        return sorted[idx] to sorted.getOrNull(idx + 1)
    }

    /** Up to [count] programmes after the one on now; when nothing is on, the next ones to start. [sorted] by start. */
    fun upcoming(sorted: List<EpgProgramme>, nowMs: Long, count: Int = 3): List<EpgProgramme> {
        if (count <= 0) return emptyList()
        val idx = sorted.indexOfFirst { it.isOnAt(nowMs) }
        val from = if (idx >= 0) idx + 1 else sorted.indexOfFirst { it.startMs > nowMs }.takeIf { it >= 0 } ?: return emptyList()
        return sorted.subList(from.coerceAtMost(sorted.size), (from + count).coerceAtMost(sorted.size)).toList()
    }

    /** "14:05" (24 h) or "2:05 PM" (12 h) in [zone]. */
    fun clock(ms: Long, use24h: Boolean, zone: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = ms }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val mm = if (m < 10) "0$m" else "$m"
        if (use24h) return (if (h < 10) "0$h" else "$h") + ":" + mm
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12:$mm " + if (h < 12) "AM" else "PM"
    }

    /** "14:00 – 15:30"; just the start when the stop is missing or not after it. */
    fun timeRange(p: EpgProgramme, use24h: Boolean, zone: TimeZone = TimeZone.getDefault()): String {
        val start = clock(p.startMs, use24h, zone)
        return if (p.stopMs > p.startMs) "$start – ${clock(p.stopMs, use24h, zone)}" else start
    }

    /** Programme title, or a placeholder for a nameless entry. */
    fun title(p: EpgProgramme): String = p.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled programme"

    /** "Next 15:00 · Title" for a list row. */
    fun nextLine(p: EpgProgramme, use24h: Boolean, zone: TimeZone = TimeZone.getDefault()): String =
        "Next ${clock(p.startMs, use24h, zone)} · ${title(p)}"
}
