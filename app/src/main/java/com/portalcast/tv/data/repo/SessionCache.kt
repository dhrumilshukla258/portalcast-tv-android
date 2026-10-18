package com.portalcast.tv.data.repo

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
) {
    private val loading = Mutex()
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
        return loading.withLock {
            if (!forceRefresh) peek()?.let { return@withLock Result.success(it) }
            load().onSuccess { put(it) }
        }
    }
}
