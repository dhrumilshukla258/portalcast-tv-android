package com.portalcast.tv.data.repo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Server answers kept in the app's storage, so pages opened before (a series, its seasons and
 * episodes, category pages, Live TV's channels) show at once after the app was closed; the
 * caches that use it ([KeyedSessionCache], [SessionCache] with a [Stored]) then ask the server
 * again in the background and, when the answer changed, store it and say so on [updates], so
 * the screen showing it reloads quietly (owner, 2026-10-22).
 *
 * One file per answer, named by a hash of user, server, kind and key: another account or server
 * never sees them, and everything goes on sign-out ([clear]). Bounded by [maxBytes]: the least
 * recently used files go first.
 */
class DiskCache(
    private val dir: File,
    private val json: Json,
    /** Background revalidation runs here (outlives the screen that asked). */
    val scope: CoroutineScope,
    /** "user@server" while signed in, else null (nothing is read or written). */
    private val identity: () -> String?,
    private val maxBytes: Long = 40L * 1024 * 1024,
) {
    private val _updates = MutableSharedFlow<String>(extraBufferCapacity = 32)

    /** The kind ([Stored.namespace]) of an answer that changed after the stored copy was shown. */
    val updates: SharedFlow<String> = _updates

    internal fun changed(namespace: String) {
        _updates.tryEmit(namespace)
    }

    private fun fileFor(namespace: String, key: String): File? {
        val id = identity() ?: return null
        val digest = MessageDigest.getInstance("SHA-1").digest("$id\u0000$namespace\u0000$key".toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".json")
    }

    suspend fun <V> read(namespace: String, key: String, serializer: KSerializer<V>): V? = withContext(Dispatchers.IO) {
        val f = fileFor(namespace, key)?.takeIf { it.isFile } ?: return@withContext null
        runCatching { json.decodeFromString(serializer, f.readText()).also { f.setLastModified(System.currentTimeMillis()) } }
            // A file from an older app version that no longer parses is dropped, not shown.
            .getOrElse { f.delete(); null }
    }

    /** Stores [value]; true when a different copy was there before (the screen should reload). */
    suspend fun <V> write(namespace: String, key: String, serializer: KSerializer<V>, value: V): Boolean = withContext(Dispatchers.IO) {
        val f = fileFor(namespace, key) ?: return@withContext false
        val text = runCatching { json.encodeToString(serializer, value) }.getOrNull() ?: return@withContext false
        val old = if (f.isFile) runCatching { f.readText() }.getOrNull() else null
        if (old == text) {
            f.setLastModified(System.currentTimeMillis())
            return@withContext false
        }
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, f.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
        trimSometimes()
        old != null
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private var writes = 0

    private fun trimSometimes() {
        if (++writes % 20 != 0) return
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes * 8 / 10) break
            total -= f.length()
            f.delete()
        }
    }
}

/** Turns on storage for one cache: its [namespace] (a kind of answer) and how to (de)serialize it. */
class Stored<V>(val disk: DiskCache, val namespace: String, val serializer: KSerializer<V>)
