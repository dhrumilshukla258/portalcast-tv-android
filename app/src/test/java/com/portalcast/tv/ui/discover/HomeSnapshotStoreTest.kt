package com.portalcast.tv.ui.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.nio.file.Files

/** The Home shown at once on a cold start belongs to one user on one server. */
class HomeSnapshotStoreTest {
    @Test
    fun `another user, another server or nobody never reads someone's Home`() = runBlocking {
        val dir = Files.createTempDirectory("pc-home").toFile()
        var who: String? = "1@http://server"
        val store = HomeSnapshotStore(dir, identity = { who })
        store.write("all", HomeSnapshot(savedAt = 42))
        assertEquals(42L, store.read("all")?.savedAt)

        who = "2@http://server"
        assertNull("another user", store.read("all"))
        who = "1@http://other"
        assertNull("another server", store.read("all"))
        who = null
        assertNull("signed out", store.read("all"))
        store.write("all", HomeSnapshot(savedAt = 7)) // nothing written while signed out

        who = "1@http://server"
        assertEquals(42L, store.read("all")?.savedAt)
        assertNull("another kind", store.read("movie"))
        Unit
    }
}
