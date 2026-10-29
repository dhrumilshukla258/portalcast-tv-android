package com.portalcast.tv.playback

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Playing in another app (owner 2026-10-29: "support any external player the user wants").
 * Settings › Player picks [BUILT_IN], [ASK] (Android's "Open with" chooser) or an installed
 * app's package. The app gets the same short-lived stream link as the built-in player (a
 * token on the PortalCast server, never the provider's or Jellyfin's address), the title and
 * where to resume; VLC, MX Player and Just Player report where the viewer stopped, which is
 * saved as progress when they return.
 */
object ExternalPlayer {
    const val BUILT_IN = ""
    const val ASK = "ask"

    private const val VLC = "org.videolan.vlc"

    data class App(val packageName: String, val label: String)

    /** Installed apps that play a video link (this app left out), by name. */
    fun installedPlayers(context: Context): List<App> {
        val probe = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("http://example.invalid/v.mp4"), "video/*")
        val pm = context.packageManager
        return runCatching {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(probe, 0)
                .mapNotNull { r ->
                    val pkg = r.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) null else App(pkg, r.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }

    /** The intent that plays [stream] in [choice] (a package, or [ASK] for the chooser). */
    fun intent(stream: ResolvedStream, title: String, resumeMs: Long?, choice: String): Intent {
        val type = if (stream.isHls) "application/x-mpegURL" else "video/*"
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(stream.url), type)
            putExtra("title", title)
            // VLC reads the position as a Long; MX Player and Just Player as an Int.
            if (resumeMs != null && resumeMs > 0) {
                if (choice == VLC) putExtra("position", resumeMs) else putExtra("position", resumeMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            }
            putExtra("from_start", resumeMs == null || resumeMs <= 0L)
            putExtra("return_result", true) // MX Player: report the position back
            if (choice != ASK && choice != BUILT_IN) setPackage(choice)
        }
        return if (choice == ASK) Intent.createChooser(view, "Play with") else view
    }

    /** Where the viewer stopped, as the other app reported it (null when it reports nothing). */
    data class Result(val positionMs: Long?, val durationMs: Long?, val completed: Boolean)

    /** Reads the extras VLC (`extra_position`, `extra_duration`) and MX / Just Player (`position`, `duration`, `end_by`) return. */
    fun result(extras: Map<String, Any?>): Result {
        fun ms(vararg keys: String): Long? = keys.firstNotNullOfOrNull { (extras[it] as? Number)?.toLong()?.takeIf { v -> v >= 0 } }
        val pos = ms("extra_position", "position")
        val dur = ms("extra_duration", "duration")
        val finished = extras["end_by"] == "playback_completion"
        return Result(pos, dur, finished)
    }

    fun result(data: Intent?): Result {
        val b = data?.extras ?: return Result(null, null, false)
        @Suppress("DEPRECATION")
        return result(b.keySet().associateWith { b.get(it) })
    }
}
