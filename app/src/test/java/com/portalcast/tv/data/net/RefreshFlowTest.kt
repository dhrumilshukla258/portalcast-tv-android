package com.portalcast.tv.data.net

import com.portalcast.tv.data.Routes
import com.portalcast.tv.data.TestStack
import com.portalcast.tv.data.bodyJson
import com.portalcast.tv.data.fakeJwt
import com.portalcast.tv.data.fixtureResponse
import com.portalcast.tv.data.json
import com.portalcast.tv.data.unauthorized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RefreshFlowTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    /** `/api/user/profile` that only accepts [token]; everything else is a 401. */
    private fun Routes.profileAccepting(token: String, onStale: () -> Unit = {}) = get("/api/user/profile") { req ->
        if (req.getHeader("Authorization") == "Bearer $token") fixtureResponse("profile") else { onStale(); unauthorized() }
    }

    @Test
    fun `two concurrent 401s produce exactly one refresh and both requests are retried`() = runBlocking {
        val stack = TestStack(server)
        val refreshCalls = AtomicInteger()
        // Hold both stale-token requests until both have arrived, so both 401s are in
        // flight at once and both authenticators race for the refresher.
        val bothArrived = CountDownLatch(2)
        stack.routes.get("/api/user/profile") { req ->
            if (req.getHeader("Authorization") == "Bearer access-new") fixtureResponse("profile") else {
                bothArrived.countDown()
                bothArrived.await(5, TimeUnit.SECONDS)
                unauthorized()
            }
        }
        stack.routes.on("POST", "/api/auth/refresh") {
            refreshCalls.incrementAndGet()
            fixtureResponse("refresh").setBodyDelay(300, TimeUnit.MILLISECONDS)
        }

        val results = (1..2).map { async(Dispatchers.IO) { stack.api.profile() } }.awaitAll()

        assertEquals(2, results.size)
        results.forEach { assertEquals("tv@example.com", it.email) }
        assertEquals("exactly one refresh call", 1, refreshCalls.get())
        assertEquals("access-new", stack.session.session.value?.accessToken)
        assertEquals("refresh token echoed back unchanged", "refresh-1", stack.session.session.value?.refreshToken)
        val refreshBody = stack.routes.requests("POST", "/api/auth/refresh").single().bodyJson()
        assertEquals("refresh-1", refreshBody["refreshToken"]?.jsonPrimitive?.content)
        val profileRequests = stack.routes.requests("GET", "/api/user/profile")
        assertEquals(2, profileRequests.count { it.getHeader("Authorization") == "Bearer access-old" })
        assertEquals(2, profileRequests.count { it.getHeader("Authorization") == "Bearer access-new" })
    }

    @Test
    fun `refresh rejected with 401 clears the session and surfaces Unauthorized`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.profileAccepting("never")
        stack.routes.on("POST", "/api/auth/refresh") { json("""{"error":"Invalid or expired refresh token"}""", 401) }

        val result = stack.auth.refreshProfile()

        assertTrue(result.exceptionOrNull() is ApiError.Unauthorized)
        assertNull("session cleared", stack.session.session.value)
        assertTrue(stack.session.clearCount.get() >= 1)
        assertEquals(1, stack.routes.count("POST", "/api/auth/refresh"))
        assertEquals("no retry after a dead refresh token", 1, stack.routes.count("GET", "/api/user/profile"))
    }

    @Test
    fun `a 401 after a successful refresh gives up instead of looping`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.profileAccepting("never")
        stack.routes.on("POST", "/api/auth/refresh") { fixtureResponse("refresh") }

        val result = stack.auth.refreshProfile()

        assertTrue(result.exceptionOrNull() is ApiError.Unauthorized)
        assertEquals(1, stack.routes.count("POST", "/api/auth/refresh"))
        assertEquals("original + one retry", 2, stack.routes.count("GET", "/api/user/profile"))
        assertNull("refreshProfile clears the session on a final 401", stack.session.session.value)
    }

    @Test
    fun `access token expiring within five minutes is refreshed before the request`() = runBlocking {
        val nowSeconds = System.currentTimeMillis() / 1000
        val stack = TestStack(server, accessToken = fakeJwt(nowSeconds + 60))
        var staleHits = 0
        stack.routes.profileAccepting("access-new") { staleHits++ }
        stack.routes.on("POST", "/api/auth/refresh") { fixtureResponse("refresh") }

        val profile = stack.api.profile()

        assertEquals("tv@example.com", profile.email)
        assertEquals(1, stack.routes.count("POST", "/api/auth/refresh"))
        assertEquals("no 401 round trip", 0, staleHits)
        assertEquals("access-new", stack.session.session.value?.accessToken)
    }

    @Test
    fun `a healthy access token is sent as-is`() = runBlocking {
        val nowSeconds = System.currentTimeMillis() / 1000
        val token = fakeJwt(nowSeconds + 3600)
        val stack = TestStack(server, accessToken = token)
        stack.routes.profileAccepting(token)

        stack.api.profile()

        assertEquals(0, stack.routes.count("POST", "/api/auth/refresh"))
        val sent = stack.routes.requests("GET", "/api/user/profile").single()
        assertEquals("Bearer $token", sent.getHeader("Authorization"))
        assertNotNull(sent.getHeader("User-Agent"))
        assertTrue(sent.getHeader("User-Agent")!!.startsWith("PortalcastTV/"))
    }

    @Test
    fun `requests without a configured server fail with NoServer before touching the network`() = runBlocking {
        val stack = TestStack(server)
        stack.session.serverUrl.value = null

        val result = apiCall("/api/user/profile") { stack.api.profile() }

        val error = result.exceptionOrNull()
        assertTrue(
            "got ${error?.javaClass?.name}: ${error?.message}; cause=${error?.cause}; suppressed=${error?.suppressed?.toList()}",
            error is ApiError.NoServer,
        )
        assertEquals(0, stack.routes.received.size)
    }
}
