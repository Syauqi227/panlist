package com.metrolist.desktop

import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.player.DesktopPlaybackManager
import com.metrolist.desktop.player.VlcAudioPlayer
import com.metrolist.desktop.repository.DesktopMusicRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SearchShuffleAndDeadlockTest {

    @Test
    fun testShufflePreservesAllSearchResultsEvenIfLastSongIsClicked() = runBlocking {
        val repository = DesktopMusicRepository()
        val player = VlcAudioPlayer()
        val manager = DesktopPlaybackManager(player, repository)

        val songs = (1..10).map { i ->
            DesktopSong(
                id = "song_$i",
                title = "Title $i",
                artist = "Artist $i",
                durationText = "3:30",
            )
        }

        // Simulate user clicking the 10th (last) song from search results
        val lastSong = songs.last()
        manager.playSong(lastSong, sourceQueue = songs)

        assertEquals("Current song should be song_10", "song_10", manager.currentSong.value?.id)
        assertEquals(9, manager.currentIndex.value)
        assertFalse("Initially shuffle should be off", manager.isShuffleEnabled.value)

        // Turn on shuffle
        manager.toggleShuffle()

        assertTrue("Shuffle should now be enabled", manager.isShuffleEnabled.value)
        assertEquals("Current song should still be song_10", "song_10", manager.currentSong.value?.id)
        assertEquals("Current index should be reset to 0 in shuffled queue", 0, manager.currentIndex.value)
        assertTrue("Queue should have next songs to play", manager.hasNext)
        assertEquals("Queue should still contain all 10 songs", 10, manager.queue.value.size)

        // All 9 other songs must be present in the upcoming queue (queue.drop(1))
        val upcomingIds = manager.queue.value.drop(1).map { it.id }.toSet()
        assertEquals(9, upcomingIds.size)
        for (i in 1..9) {
            assertTrue("song_$i should be in the upcoming queue", upcomingIds.contains("song_$i"))
        }

        // Turn off shuffle
        manager.toggleShuffle()
        assertFalse("Shuffle should now be disabled", manager.isShuffleEnabled.value)
        assertEquals("Restored queue size should be 10", 10, manager.queue.value.size)
        assertEquals("Current song should still be song_10", "song_10", manager.currentSong.value?.id)
        assertEquals("Current index should be restored to 9", 9, manager.currentIndex.value)

        player.release()
    }

    @Test
    fun testVlcAudioPlayerNonBlockingToggle() = runBlocking {
        val player = VlcAudioPlayer()

        // Calling togglePlayPause on UI thread should not throw or deadlock
        assertFalse(player.isPlaying.value)
        player.togglePlayPause()

        // Stop and release should also be safe and non-blocking
        player.stop()
        player.release()
    }
}
