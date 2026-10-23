package com.portalcast.tv.data.repo

import kotlinx.coroutines.launch
import com.portalcast.tv.data.store.SessionSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory cache scoped to the signed-in session (user id + refresh-token issue
 * time, so a re-login as anyone starts empty) with an optional TTL. Loads are
 * single-flight: concurrent callers share one fetch. Failures are never cached.
 *
 * With no [session] the cache lives for the process; callers that need
 * per-login invalidation must pass the session source.
 */
internal class SessionCache<T : Any>(
    private val session: SessionSource?,
    private val ttlMs: Long = Long.MAX_VALUE,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Also kept in storage (DiskCache): after a restart shown at once, then checked with the server. */
    private val stored: Stored<T>? = null,
) {
    private val loading = Mutex()
    @Volatile private var revalidating = false
    private val lock = Any()
    private var key: String? = null
    private var value: T? = null
    private var storedAt = 0L

    private fun sessionKey(): String? = session?.session?.value?.let { "${it.user.id}@${it.refreshIssuedAt}" }

    /** The cached value if it belongs to the current session and has not expired. */
    fun peek(): T? = synchronized(lock) {
        val v = value ?: return null
        if (key != sessionKey()) return null
        if (ttlMs != Long.MAX_VALUE && clock() - storedAt >= ttlMs) return null
        v
    }

    fun put(v: T) = synchronized(lock) {
        key = sessionKey()
        value = v
        storedAt = clock()
    }

    fun clear() = synchronized(lock) {
        key = null
        value = null
    }

    suspend fun get(forceRefresh: Boolean = false, load: suspend () -> Result<T>): Result<T> {
        if (!forceRefresh) peek()?.let { return Result.success(it) }
        if (!forceRefresh && stored != null) {
            stored.disk.read(stored.namespace, "", stored.serializer)?.let { v ->
                put(v)
                revalidate(load)
                return Result.success(v)
            }
        }
        return loading.withLock {
            if (!forceRefresh) peek()?.let { return@withLock Result.success(it) }
            load().onSuccess { v ->
                put(v)
                stored?.let { st -> st.disk.write(st.namespace, "", st.serializer, v) }
            }
        }
    }

    /** The stored copy was shown: ask the server again; a changed answer replaces it and is announced. */
    private fun revalidate(load: suspend () -> Result<T>) {
        val st = stored ?: return
        if (revalidating) return
        revalidating = true
        st.disk.scope.launch {
            try {
                load().onSuccess { v ->
                    put(v)
                    if (st.disk.write(st.namespace, "", st.serializer, v)) st.disk.changed(st.namespace)
                }
            } finally {
                revalidating = false
            }
        }
    }
}
