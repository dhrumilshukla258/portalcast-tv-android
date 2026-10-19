package com.portalcast.tv.data.repo

import com.portalcast.tv.data.FakeSessionSource
import com.portalcast.tv.data.model.UserDto
import com.portalcast.tv.data.store.Session
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class KeyedSessionCacheTest {
    private val user = UserDto(id = 1, email = "tv@example.com", name = "TV", role = "user")
    private fun session(issued: Long = 1L) = FakeSessionSource("http://s", Session("a", "r", issued, user))

    @Test
    fun `second get of a key is served from memory`() = runTest {
        val calls = AtomicInteger()
        val cache = KeyedSessionCache<String, Int>(session())
        repeat(3) { assertEquals(7, cache.get("k") { calls.incrementAndGet(); Result.success(7) }.getOrThrow()) }
        assertEquals(1, calls.get())
    }

    @Test
    fun `entries expire after the ttl`() = runTest {
        var now = 0L
        val calls = AtomicInteger()
        val cache = KeyedSessionCache<String, Int>(session(), ttlMs = 1000, clock = { now })
        cache.get("k") { calls.incrementAndGet(); Result.success(1) }
        now = 999; cache.get("k") { calls.incrementAndGet(); Result.success(2) }
        assertEquals(1, calls.get())
        now = 1000; assertEquals(3, cache.get("k") { calls.incrementAndGet(); Result.success(3) }.getOrThrow())
        assertEquals(2, calls.get())
    }

    @Test
    fun `failures are not cached`() = runTest {
        val calls = AtomicInteger()
        val cache = KeyedSessionCache<String, Int>(session())
        assertTrue(cache.get("k") { calls.incrementAndGet(); Result.failure(IllegalStateException()) }.isFailure)
        assertEquals(5, cache.get("k") { calls.incrementAndGet(); Result.success(5) }.getOrThrow())
        assertEquals(2, calls.get())
    }

    @Test
    fun `a new sign-in starts empty`() = runTest {
        val src = session(issued = 1L)
        val cache = KeyedSessionCache<String, Int>(src)
        cache.get("k") { Result.success(1) }
        src.session.value = Session("a2", "r2", 2L, user)
        assertEquals(null, cache.peek("k"))
    }

    @Test
    fun `least recently used entries are evicted beyond the limit`() = runTest {
        val cache = KeyedSessionCache<String, Int>(session(), maxEntries = 2)
        cache.put("a", 1); cache.put("b", 2)
        cache.peek("a") // touch a
        cache.put("c", 3) // evicts b
        assertEquals(1, cache.peek("a")); assertEquals(null, cache.peek("b")); assertEquals(3, cache.peek("c"))
    }

    @Test
    fun `concurrent loads of one key share a single request`() = runTest {
        val calls = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val cache = KeyedSessionCache<String, Int>(session())
        val a = async { cache.get("k") { calls.incrementAndGet(); gate.await(); Result.success(9) } }
        val b = async { cache.get("k") { calls.incrementAndGet(); Result.success(10) } }
        testScheduler.runCurrent()
        gate.complete(Unit)
        assertEquals(9, a.await().getOrThrow()); assertEquals(9, b.await().getOrThrow())
        assertEquals(1, calls.get())
    }
}
