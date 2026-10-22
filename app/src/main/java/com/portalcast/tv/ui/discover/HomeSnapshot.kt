package com.portalcast.tv.ui.discover

import com.portalcast.tv.data.model.HomeResponse
import com.portalcast.tv.data.model.MediaItem
import com.portalcast.tv.data.model.ProgressRecord
import com.portalcast.tv.data.net.AppJson
import kotlinx.serialization.Serializable
import java.io.File

/**
 * The last Home a viewer saw (plan Phase 3a, "saved screens at start"): the server's rows,
 * What's new and Continue Watching, written after each load and shown at once on the next
 * start while fresh ones load. Genre rows are not kept (they need the facets, which load
 * with everything else). One small file per screen type (Home, Movies, Series).
 */
@Serializable
data class HomeSnapshot(
    val home: HomeResponse? = null,
    val whatsNew: List<MediaItem> = emptyList(),
    val totalTitles: Int? = null,
    val continueWatching: List<ProgressRecord> = emptyList(),
    val savedAt: Long = 0,
)

/** Files in the app's private storage; a broken or old file is simply not used. */
class HomeSnapshotStore(private val dir: File) {
    private fun file(key: String) = File(dir, "home-${key.replace(Regex("[^a-z]"), "")}.json")

    fun read(key: String): HomeSnapshot? = runCatching {
        AppJson.decodeFromString(HomeSnapshot.serializer(), file(key).readText())
    }.getOrNull()

    fun write(key: String, snapshot: HomeSnapshot) {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "${file(key).name}.tmp")
            tmp.writeText(AppJson.encodeToString(HomeSnapshot.serializer(), snapshot))
            tmp.renameTo(file(key))
        }
    }

    /** Signed out or another server: the next viewer must not see these. */
    fun clear() { runCatching { dir.listFiles()?.forEach { it.delete() } } }
}
