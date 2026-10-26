package com.metrolist.desktop

import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.repository.RecentSearchStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecentSearchStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testRecentSearchAddRetrieveAndDeduplicate() = runBlocking {
        val testDir = tempFolder.newFolder("recent_test")
        val store = RecentSearchStore(storageDir = testDir, maxItems = 3)

        val song1 = DesktopSong("id1", "golden hour", "JVKE", "3:29")
        val song2 = DesktopSong("id2", "happier", "Olivia Rodrigo", "2:55")
        val song3 = DesktopSong("id3", "2002", "Anne-Marie", "3:07")
        val song4 = DesktopSong("id4", "Ghost", "Justin Bieber", "2:33")

        // Initial should be empty
        assertEquals(0, store.getRecentSearches().size)

        // Add 3 songs
        store.addRecentSearch(song1)
        store.addRecentSearch(song2)
        store.addRecentSearch(song3)

        var list = store.getRecentSearches()
        assertEquals(3, list.size)
        assertEquals("id3", list[0].id) // Most recent first
        assertEquals("id2", list[1].id)
        assertEquals("id1", list[2].id)

        // Adding existing song moves it to the top without duplicate
        store.addRecentSearch(song1)
        list = store.getRecentSearches()
        assertEquals(3, list.size)
        assertEquals("id1", list[0].id)
        assertEquals("id3", list[1].id)
        assertEquals("id2", list[2].id)

        // Adding 4th song drops the oldest due to maxItems = 3
        store.addRecentSearch(song4)
        list = store.getRecentSearches()
        assertEquals(3, list.size)
        assertEquals("id4", list[0].id)
        assertEquals("id1", list[1].id)
        assertEquals("id3", list[2].id)
        assertFalse(list.any { it.id == "id2" })

        // Remove item
        val removed = store.removeRecentSearch("id1")
        assertTrue(removed)
        list = store.getRecentSearches()
        assertEquals(2, list.size)
        assertFalse(list.any { it.id == "id1" })

        // Clear all
        store.clearAll()
        assertEquals(0, store.getRecentSearches().size)
    }

    @Test
    fun testPersistenceAcrossInstances() = runBlocking {
        val testDir = tempFolder.newFolder("recent_persistence")
        val store1 = RecentSearchStore(storageDir = testDir)
        val song = DesktopSong("s1", "That's So True", "Gracie Abrams", "3:45")
        store1.addRecentSearch(song)

        val store2 = RecentSearchStore(storageDir = testDir)
        val list = store2.getRecentSearches()
        assertEquals(1, list.size)
        assertEquals("s1", list[0].id)
        assertEquals("That's So True", list[0].title)
    }
}
