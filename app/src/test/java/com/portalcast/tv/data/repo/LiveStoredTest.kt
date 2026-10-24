package com.portalcast.tv.data.repo

import com.portalcast.tv.data.model.Channel
import com.portalcast.tv.data.model.ChannelGroup
import com.portalcast.tv.data.net.AppJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.nio.file.Files

/**
 * Live TV's groups and channels are stored apart: in one slot the channels overwrote the
 * groups, and after a restart "21 Jump Street FHD" showed in the Live TV group column (a
 * channel reads back as a group: both have a name).
 */
class LiveStoredTest {
    private fun disk(dir: java.io.File) = DiskCache(dir, AppJson, CoroutineScope(Dispatchers.Default), identity = { "1@http://server" })
    private val groupsSer = ListSerializer(ChannelGroup.serializer())
    private val channelsSer = ListSerializer(Channel.serializer())

    @Test
    fun `after a restart the groups come back as groups, whatever was stored last`() = runBlocking {
        assertNotEquals(DefaultLiveRepository.GROUPS_NS, DefaultLiveRepository.CHANNELS_NS)
        val dir = Files.createTempDirectory("pc-live").toFile()
        val groups = listOf(ChannelGroup(id = "70", title = "ENGLISH TV SHOWS | 24/7"))
        val channels = listOf(Channel(id = "94816", name = "21 Jump Street FHD", groupId = "70"))

        // First run: groups, then channels, as Live TV loads them.
        val store = disk(dir)
        SessionCache(null, stored = Stored(store, DefaultLiveRepository.GROUPS_NS, "groups", groupsSer)).get { Result.success(groups) }
        SessionCache(null, stored = Stored(store, DefaultLiveRepository.CHANNELS_NS, "channels", channelsSer)).get { Result.success(channels) }

        // "Restart": the stored copies are shown before the server answers.
        val again = disk(dir)
        val shownGroups = SessionCache(null, stored = Stored(again, DefaultLiveRepository.GROUPS_NS, "groups", groupsSer)).get { Result.success(emptyList()) }.getOrThrow()
        val shownChannels = SessionCache(null, stored = Stored(again, DefaultLiveRepository.CHANNELS_NS, "channels", channelsSer)).get { Result.success(emptyList()) }.getOrThrow()
        assertEquals(listOf("ENGLISH TV SHOWS | 24/7"), shownGroups.map { it.displayTitle })
        assertEquals(listOf("21 Jump Street FHD"), shownChannels.map { it.name })
    }
}

/** Two caches of one kind never share a file, even with the same key (Series' groups are kept
 *  under "", and a season list of a series with an empty id would have overwritten them). */
class StoredSlotTest {
    @Test
    fun `the same key in two slots of one kind stays apart`() = runBlocking {
        val dir = Files.createTempDirectory("pc-slots").toFile()
        val store = DiskCache(dir, AppJson, CoroutineScope(Dispatchers.Default), identity = { "1@http://server" })
        val ser = ListSerializer(String.serializer())
        val groups = Stored(store, "series", "groups", ser)
        val seasons = Stored(store, "series", "seasons", ser)
        store.write("series", groups.key(""), ser, listOf("a group"))
        store.write("series", seasons.key(""), ser, listOf("a season"))
        assertEquals(listOf("a group"), store.read("series", groups.key(""), ser))
        assertEquals(listOf("a season"), store.read("series", seasons.key(""), ser))
    }
}
