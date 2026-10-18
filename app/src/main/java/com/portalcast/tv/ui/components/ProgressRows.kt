package com.portalcast.tv.ui.components

import com.portalcast.tv.data.model.ProgressRecord

/** Reading `GET /api/user/progress` rows for display (contract §6.2). Pure, JVM-tested. */
object ProgressRows {
    /** At or below this a row is not resumable (same 2 s rule as the progress reporter). */
    const val RESUME_MIN_SEC = 2.0

    /** Resume position in ms for the row keyed [mediaId], or null when absent, completed or ≤ 2 s. */
    fun resumableMs(rows: List<ProgressRecord>, mediaId: String): Long? {
        if (mediaId.isBlank()) return null
        val row = rows.firstOrNull { it.mediaId == mediaId } ?: return null
        return resumableMs(row)
    }

    /** Resume position in ms of one row, or null when completed or ≤ 2 s. */
    fun resumableMs(row: ProgressRecord): Long? {
        if (row.completed || row.progress <= RESUME_MIN_SEC) return null
        return (row.progress * 1000).toLong()
    }

    /** Watched fraction in 0..1: 1 when completed, else `meta.progressPercent`, else progress / meta.duration. */
    fun fraction(row: ProgressRecord): Float? {
        if (row.completed) return 1f
        val meta = row.parsedMeta
        meta?.progressPercent?.takeIf { it > 0 }?.let { return (it / 100f).coerceIn(0f, 1f) }
        val duration = meta?.duration ?: return null
        if (duration <= 0.0) return null
        return (row.progress / duration).toFloat().coerceIn(0f, 1f)
    }
}
