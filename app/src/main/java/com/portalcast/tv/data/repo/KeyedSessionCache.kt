package com.portalcast.tv.data.repo

import kotlinx.coroutines.launch
import com.portalcast.tv.data.store.SessionSource
import kotlinx.coroutines.CompletableDeferred

/**
 * Many-entry companion to [SessionCache]: one value per key (a page, a query, a season),
 * scoped to the signed-in session, expiring after [ttlMs], least-recently-used entries
 * evicted beyond [maxEntries]. Concurrent loads of the same key share one request.
 * Failures are never cached.
 *
 * Sizing (measured on the live catalog): a page of 14–40 titles is ~30–100 KB in memory,
 * so the default 150 entries stay under ~8 MB even when every entry is a full page.
 */
internal class KeyedSessionCache<K : Any, V : Any>(
    private val session: SessionSource?,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Also kept in storage (DiskCache): after a restart shown at once, then checked with the server. */
    private val stored: Stored<V>? = null,
) {
    private class Entry<V>(val sessionKey: String?, val storedAt: Long, val value: V)

    private val lock = Any()
    private val entries = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean = size > maxEntries
    }
    private val inFlight = HashMap<K, CompletableDeferred<Result<V>>>()
    private val revalidating = HashSet<K>()

    private fun sessionKey(): String? = session?.session?.value?.let { "${it.user.id}@${it.refreshIssuedAt}" }

    fun peek(key: K): V? = synchronized(lock) {
        val e = entries[key] ?: return null
        if (e.sessionKey != sessionKey() || clock() - e.storedAt >= ttlMs) {
            entries.remove(key)
            return null
        }
        e.value
    }

    fun put(key: K, value: V) = synchronized(lock) {
        entries[key] = Entry(sessionKey(), clock(), value)
    }

    fun clear() = synchronized(lock) { entries.clear() }

    val size: Int get() = synchronized(lock) { entries.size }

    suspend fun get(key: K, forceRefresh: Boolean = false, load: suspend () -> Result<V>): Result<V> {
        if (!forceRefresh) peek(key)?.let { return Result.success(it) }
        if (!forceRefresh && stored != null) {
            stored.disk.read(stored.namespace, stored.key(key.toString()), stored.serializer)?.let { v ->
                put(key, v)
                revalidate(key, load)
                return Result.success(v)
            }
        }
        val (deferred, owner) = synchronized(lock) {
            val existing = inFlight[key]
            if (existing != null && !forceRefresh) existing to false
            else CompletableDeferred<Result<V>>().also { inFlight[key] = it } to true
        }
        if (!owner) return deferred.await()
        val result = try {
            load()
        } catch (t: Throwable) {
            synchronized(lock) { if (inFlight[key] === deferred) inFlight.remove(key) }
            deferred.completeExceptionally(t)
            throw t
        }
        synchronized(lock) {
            result.onSuccess { entries[key] = Entry(sessionKey(), clock(), it) }
            if (inFlight[key] === deferred) inFlight.remove(key)
        }
        if (stored != null) result.onSuccess { stored.disk.write(stored.namespace, stored.key(key.toString()), stored.serializer, it) }
        deferred.complete(result)
        return result
    }

    /** The stored copy was shown: ask the server again; a changed answer replaces it and is announced. */
    private fun revalidate(key: K, load: suspend () -> Result<V>) {
        val st = stored ?: return
        synchronized(lock) { if (!revalidating.add(key)) return }
        st.disk.scope.launch {
            try {
                load().onSuccess { v ->
                    put(key, v)
                    if (st.disk.write(st.namespace, st.key(key.toString()), st.serializer, v)) st.disk.changed(st.namespace)
                }
            } finally {
                synchronized(lock) { revalidating.remove(key) }
            }
        }
    }

    companion object {
        const val DEFAULT_TTL_MS = 15 * 60 * 1000L
        const val DEFAULT_MAX_ENTRIES = 150
    }
}
