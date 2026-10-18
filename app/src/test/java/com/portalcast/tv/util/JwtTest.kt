package com.portalcast.tv.util

import com.portalcast.tv.data.fakeJwt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JwtTest {

    @Test
    fun `reads exp from a base64url payload without padding`() {
        val token = fakeJwt(1_800_000_000L)
        assertEquals(1_800_000_000L, Jwt.expiresAt(token))
        assertEquals("tv@example.com", Jwt.payload(token)!!["email"].toString().trim('"'))
    }

    @Test
    fun `expiresWithin and isExpired use the supplied clock`() {
        val token = fakeJwt(10_000L)
        assertTrue(Jwt.expiresWithin(token, 300, nowSeconds = 9_800L))
        assertFalse(Jwt.expiresWithin(token, 300, nowSeconds = 9_000L))
        assertTrue(Jwt.isExpired(token, nowSeconds = 10_000L))
        assertFalse(Jwt.isExpired(token, nowSeconds = 9_999L))
    }

    @Test
    fun `garbage tokens never throw`() {
        assertNull(Jwt.payload("not-a-jwt"))
        assertNull(Jwt.payload("a.!!!.c"))
        assertNull(Jwt.expiresAt("a.e30.c"))          // {} has no exp
        assertFalse(Jwt.expiresWithin("", 300))
    }

    @Test
    fun `decoder handles standard and url alphabets with or without padding`() {
        val bytes = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0x3E, 0x01, 0x02)
        val std = java.util.Base64.getEncoder().encodeToString(bytes)
        val url = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        assertArrayEquals(bytes, Jwt.decodeBase64Url(std))
        assertArrayEquals(bytes, Jwt.decodeBase64Url(url))
        assertArrayEquals("hi".toByteArray(), Jwt.decodeBase64Url("aGk="))
        assertArrayEquals("hi".toByteArray(), Jwt.decodeBase64Url("aGk"))
    }
}
