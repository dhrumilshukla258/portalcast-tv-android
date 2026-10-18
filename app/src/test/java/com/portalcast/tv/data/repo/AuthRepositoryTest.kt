package com.portalcast.tv.data.repo

import com.portalcast.tv.data.TestStack
import com.portalcast.tv.data.bodyJson
import com.portalcast.tv.data.fixtureResponse
import com.portalcast.tv.data.json
import com.portalcast.tv.data.net.ApiError
import com.portalcast.tv.data.unauthorized
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

class AuthRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    @Test
    fun `setServerUrl treats a 401 probe as reachable and stores the normalised origin`() = runBlocking {
        val stack = TestStack(server, signedIn = false)
        stack.session.serverUrl.value = null
        stack.routes.get("/api/v2/provider-key") { unauthorized() }

        val result = stack.auth.setServerUrl("  ${server.hostName}:${server.port}/  ")

        assertTrue(result.isSuccess)
        assertEquals("http://${server.hostName}:${server.port}", stack.session.serverUrl.value)
        val probe = stack.routes.requests("GET", "/api/v2/provider-key").single()
        assertNull("probe is unauthenticated", probe.getHeader("Authorization"))
    }

    @Test
    fun `setServerUrl keeps a base path and clears a session that belonged to another origin`() = runBlocking {
        val stack = TestStack(server, signedIn = true)
        stack.session.serverUrl.value = "http://old-server.invalid:3000"
        stack.routes.get("/portalcast/api/v2/provider-key") { json("""{"providerKey":"xtream_host"}""") }

        val result = stack.auth.setServerUrl("http://${server.hostName}:${server.port}/portalcast/")

        assertTrue(result.isSuccess)
        assertEquals("http://${server.hostName}:${server.port}/portalcast", stack.session.serverUrl.value)
        assertNull("tokens for the old origin are dropped", stack.session.session.value)
    }

    @Test
    fun `setServerUrl fails with Network when nothing answers and NoServer when blank`() = runBlocking {
        val stack = TestStack(server, signedIn = false)
        val closed = MockWebServer().also { it.start() }
        val deadUrl = "http://${closed.hostName}:${closed.port}"
        closed.shutdown()

        val unreachable = stack.auth.setServerUrl(deadUrl)
        assertTrue(unreachable.exceptionOrNull() is ApiError.Network)
        assertEquals("url unchanged on failure", stack.origin, stack.session.serverUrl.value)

        assertTrue(stack.auth.setServerUrl("   ").exceptionOrNull() is ApiError.NoServer)
    }

    @Test
    fun `loginWithPassword sends clientType tv without a bearer and saves the session`() = runBlocking {
        val stack = TestStack(server, signedIn = false)
        stack.routes.on("POST", "/api/auth/login") { fixtureResponse("login") }

        val result = stack.auth.loginWithPassword(" user@example.com ", "s3cret")

        assertTrue(result.isSuccess)
        val request = stack.routes.requests("POST", "/api/auth/login").single()
        assertNull(request.getHeader("Authorization"))
        val body = request.bodyJson()
        assertEquals("user@example.com", body["email"]?.jsonPrimitive?.content)
        assertEquals("s3cret", body["password"]?.jsonPrimitive?.content)
        assertEquals("tv", body["clientType"]?.jsonPrimitive?.content)
        val session = stack.session.session.value
        assertNotNull(session)
        assertEquals("access-login", session!!.accessToken)
        assertEquals("refresh-login", session.refreshToken)
        assertEquals(3, session.user.id)
        assertTrue(session.user.isAdmin)
        assertEquals("login", session.user.displayName)
    }

    @Test
    fun `loginWithPassword maps 401 and 429 and never retries`() = runBlocking {
        val stack = TestStack(server, signedIn = false)
        stack.routes.on("POST", "/api/auth/login") { json("""{"error":"Invalid credentials"}""", 401) }
        val bad = stack.auth.loginWithPassword("a@b.c", "x")
        assertTrue(bad.exceptionOrNull() is ApiError.Unauthorized)
        assertEquals("Invalid credentials", bad.exceptionOrNull()!!.message)
        assertNull(stack.session.session.value)

        stack.routes.on("POST", "/api/auth/login") { json("""{"error":"Too Many Requests"}""", 429) }
        assertTrue(stack.auth.loginWithPassword("a@b.c", "x").exceptionOrNull() is ApiError.RateLimited)
        assertEquals(2, stack.routes.count("POST", "/api/auth/login"))
    }

    @Test
    fun `device code flow parses the code and persists the session on authorized`() = runBlocking {
        val stack = TestStack(server, signedIn = false)
        stack.routes.on("POST", "/api/auth/device/code") { fixtureResponse("device-code") }
        var polls = 0
        stack.routes.on("POST", "/api/auth/device/poll") {
            polls++
            if (polls < 2) fixtureResponse("device-poll-pending") else fixtureResponse("device-poll-authorized")
        }

        val code = stack.auth.requestDeviceCode().getOrThrow()
        assertEquals("ABC-DEF", code.userCode)
        assertEquals(300, code.expiresIn)
        assertTrue(code.verificationUrl.endsWith("/#/verify?code=ABC-DEF"))

        val pending = stack.auth.pollDeviceCode(code.deviceCode).getOrThrow()
        assertTrue(pending.isPending)
        assertNull(stack.session.session.value)

        val done = stack.auth.pollDeviceCode(code.deviceCode).getOrThrow()
        assertTrue(done.isAuthorized)
        assertEquals(code.deviceCode, stack.routes.requests("POST", "/api/auth/device/poll").last().bodyJson()["deviceCode"]?.jsonPrimitive?.content)
        assertEquals("access-paired", stack.session.session.value?.accessToken)
        assertEquals("paired@example.com", stack.session.session.value?.user?.email)
    }

    @Test
    fun `expired poll and inactive user are reported without touching the session`() = runBlocking {
        val stack = TestStack(server, signedIn = false)
        stack.routes.on("POST", "/api/auth/device/poll") { json("""{"status":"expired"}""") }
        assertTrue(stack.auth.pollDeviceCode("x").getOrThrow().isExpired)

        stack.routes.on("POST", "/api/auth/device/poll") { json("""{"error":"User is disabled"}""", 403) }
        val forbidden = stack.auth.pollDeviceCode("x").exceptionOrNull()
        assertTrue(forbidden is ApiError.Forbidden)
        assertEquals("User is disabled", forbidden!!.message)
        assertNull(stack.session.session.value)
    }

    @Test
    fun `refreshProfile updates the stored user and logout clears it`() = runBlocking {
        val stack = TestStack(server)
        stack.routes.get("/api/user/profile", "profile")

        val user = stack.auth.refreshProfile().getOrThrow()

        assertEquals("Living Room TV", user.name)
        assertEquals("Living Room TV", stack.session.session.value?.user?.name)
        assertEquals("access-old", stack.session.session.value?.accessToken)

        stack.auth.logout()
        assertNull(stack.session.session.value)
        assertEquals("server url survives logout", stack.origin, stack.session.serverUrl.value)
    }
}
