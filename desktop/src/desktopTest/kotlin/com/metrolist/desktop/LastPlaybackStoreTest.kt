package com.metrolist.desktop

import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.repository.LastPlaybackStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LastPlaybackStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSaveAndLoadLastPlayback() = runBlocking {
        val testDir = tempFolder.newFolder("playback_test")
        val store = LastPlaybackStore(storageDir = testDir)

        // Initially null when file doesn't exist
        assertNull(store.loadLastPlayback())

        val song1 = DesktopSong(
            id = "test_song_1",
            title = "Starboy",
            artist = "The Weeknd",
            durationText = "3:50",
            thumbnailUrl = "https://example.com/starboy.jpg",
            album = "Starboy",
        )
        val song2 = DesktopSong(
            id = "test_song_2",
            title = "Blinding Lights",
            artist = "The Weeknd",
            durationText = "3:20",
            thumbnailUrl = "https://example.com/blinding.jpg",
            album = "After Hours",
        )
        val queue = listOf(song1, song2)

        // Save last playback
        store.saveLastPlayback(
            song = song1,
            queue = queue,
            currentIndex = 0,
            playlistTitle = "The Weeknd Hits",
            positionMs = 45000L,
        )

        val loaded = store.loadLastPlayback()
        assertNotNull(loaded)
        assertEquals("test_song_1", loaded?.currentSong?.id)
        assertEquals("Starboy", loaded?.currentSong?.title)
        assertEquals("The Weeknd", loaded?.currentSong?.artist)
        assertEquals(2, loaded?.queue?.size)
        assertEquals(0, loaded?.currentIndex)
        assertEquals("The Weeknd Hits", loaded?.playlistTitle)
        assertEquals(45000L, loaded?.lastPositionMs)

        // Update with song2
        store.saveLastPlayback(
            song = song2,
            queue = queue,
            currentIndex = 1,
            playlistTitle = "The Weeknd Hits",
            positionMs = 12000L,
        )

        val updated = store.loadLastPlayback()
        assertNotNull(updated)
        assertEquals("test_song_2", updated?.currentSong?.id)
        assertEquals("Blinding Lights", updated?.currentSong?.title)
        assertEquals(1, updated?.currentIndex)

        // Clear playback
        store.clearLastPlayback()
        assertNull(store.loadLastPlayback())
    }
}
