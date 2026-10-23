package com.portalcast.tv.data.repo

import com.portalcast.tv.data.net.AppJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Answers kept in storage: shown at once after a restart, then checked with the server (DiskCache). */
class StoredCacheTest {
    private fun disk(dir: java.io.File, who: String? = "1@http://server") =
        DiskCache(dir, AppJson, CoroutineScope(Dispatchers.Default), identity = { who })

    @Test
    fun `after a restart the stored answer shows at once and a changed answer replaces it`() = runBlocking {
        val dir = Files.createTempDirectory("pc-store").toFile()
        val ser = ListSerializer(String.serializer())

        // First run: the server's answer is returned and stored.
        val first = KeyedSessionCache<String, List<String>>(null, stored = Stored(disk(dir), "series", ser))
        assertEquals(listOf("S1"), first.get("seasons|42") { Result.success(listOf("S1")) }.getOrThrow())

        // "Restart": a new cache over the same folder. The stored copy comes back without waiting
        // for the server, which now has a second season.
        val store = disk(dir)
        val second = KeyedSessionCache<String, List<String>>(null, stored = Stored(store, "series", ser))
        val changed = async { withTimeout(5_000) { store.updates.first() } }
        var asked = 0
        val shown = second.get("seasons|42") { asked++; Result.success(listOf("S1", "S2")) }.getOrThrow()
        assertEquals(listOf("S1"), shown)

        // The background check stores the new answer and says so; memory now holds it.
        assertEquals("series", changed.await())
        assertEquals(1, asked)
        assertEquals(listOf("S1", "S2"), second.peek("seasons|42"))
        assertEquals(listOf("S1", "S2"), store.read("series", "seasons|42", ser))
    }

    @Test
    fun `another account never sees what was stored`() = runBlocking {
        val dir = Files.createTempDirectory("pc-store").toFile()
        val ser = ListSerializer(String.serializer())
        KeyedSessionCache<String, List<String>>(null, stored = Stored(disk(dir, "1@s"), "live", ser)).get("k") { Result.success(listOf("mine")) }
        assertEquals(null, disk(dir, "2@s").read("live", "k", ser))
        assertEquals(null, disk(dir, null).read("live", "k", ser))
        assertTrue(dir.listFiles()!!.isNotEmpty())
    }
}
