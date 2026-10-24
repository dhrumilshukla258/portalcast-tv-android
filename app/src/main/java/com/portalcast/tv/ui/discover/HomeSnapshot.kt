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

/**
 * Files in the app's private storage; a broken or old file is simply not used. Named by user,
 * server and kind ([identity] as in DiskCache): a file left by someone else (signed out while the
 * app was not running) is never shown to the next person; signed out, nothing is read or written.
 */
class HomeSnapshotStore(private val dir: File, private val identity: () -> String? = { null }) {
    private fun file(key: String): File? {
        val id = identity() ?: return null
        val digest = java.security.MessageDigest.getInstance("SHA-1").digest("$id\u0000home\u0000$key".toByteArray())
        return File(dir, "home-" + digest.joinToString("") { "%02x".format(it) } + ".json")
    }

    fun read(key: String): HomeSnapshot? = runCatching {
        val f = file(key) ?: return null
        AppJson.decodeFromString(HomeSnapshot.serializer(), f.readText())
    }.getOrNull()

    fun write(key: String, snapshot: HomeSnapshot) {
        val f = file(key) ?: return
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "${f.name}.tmp")
            tmp.writeText(AppJson.encodeToString(HomeSnapshot.serializer(), snapshot))
            tmp.renameTo(f)
        }
    }

    /** Signed out or another server: the next viewer must not see these. */
    fun clear() { runCatching { dir.listFiles()?.forEach { it.delete() } } }
}
