package com.metrolist.desktop

import com.metrolist.desktop.model.DesktopPlaylist
import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.player.DesktopPlaybackManager
import com.metrolist.desktop.player.RepeatMode
import com.metrolist.desktop.player.VlcAudioPlayer
import com.metrolist.desktop.repository.DesktopMusicRepository
import com.metrolist.desktop.repository.LocalPlaylistStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlaylistFeatureTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testLocalPlaylistStoreCrudAndPersistence() = runBlocking {
        val testDir = tempFolder.newFolder("metrolist_test")
        val store = LocalPlaylistStore(storageDir = testDir)

        // 1. Initial store should provide default "Favorit Saya"
        val initialPlaylists = store.getPlaylists()
        assertEquals(1, initialPlaylists.size)
        assertEquals("Favorit Saya", initialPlaylists[0].title)

        // 2. Create new custom playlist
        val created = store.createPlaylist("Chill Beats", "Lagu santai untuk coding")
        assertEquals("Chill Beats", created.title)
        assertEquals("Lagu santai untuk coding", created.description)
        assertTrue(created.isLocal)

        val afterCreate = store.getPlaylists()
        assertEquals(2, afterCreate.size)
        assertTrue(afterCreate.any { it.id == created.id })

        // 3. Add songs to the playlist
        val song1 = DesktopSong(
            id = "song_1",
            title = "Midnight City",
            artist = "M83",
            durationText = "4:03",
            thumbnailUrl = "https://example.com/thumb1.jpg"
        )
        val song2 = DesktopSong(
            id = "song_2",
            title = "Starboy",
            artist = "The Weeknd",
            durationText = "3:50",
            thumbnailUrl = "https://example.com/thumb2.jpg"
        )

        val addResult1 = store.addSongToPlaylist(created.id, song1)
        assertTrue(addResult1)
        val addResult2 = store.addSongToPlaylist(created.id, song2)
        assertTrue(addResult2)

        // Duplicate addition should be gracefully handled
        val addResultDuplicate = store.addSongToPlaylist(created.id, song1)
        assertTrue(addResultDuplicate)

        val updatedPlaylist = store.getPlaylistById(created.id)
        assertNotNull(updatedPlaylist)
        assertEquals(2, updatedPlaylist!!.songs.size)
        assertEquals("Midnight City", updatedPlaylist.songs[0].title)
        assertEquals("Starboy", updatedPlaylist.songs[1].title)

        // 4. Remove a song
        val removeResult = store.removeSongFromPlaylist(created.id, "song_1")
        assertTrue(removeResult)
        val afterRemove = store.getPlaylistById(created.id)
        assertEquals(1, afterRemove!!.songs.size)
        assertEquals("song_2", afterRemove.songs[0].id)

        // 5. Test Persistence: Reload store from the same directory
        val reloadedStore = LocalPlaylistStore(storageDir = testDir)
        val reloadedPlaylists = reloadedStore.getPlaylists()
        assertEquals(2, reloadedPlaylists.size)
        val reloadedChillBeats = reloadedStore.getPlaylistById(created.id)
        assertNotNull(reloadedChillBeats)
        assertEquals("Chill Beats", reloadedChillBeats!!.title)
        assertEquals(1, reloadedChillBeats.songs.size)
        assertEquals("Starboy", reloadedChillBeats.songs[0].title)

        // 6. Delete playlist
        val deleteResult = store.deletePlaylist(created.id)
        assertTrue(deleteResult)
        assertEquals(1, store.getPlaylists().size)
    }

    @Test
    fun testExtractPlaylistId() {
        val repo = DesktopMusicRepository()

        // Plain playlist ID
        assertEquals("RDCLAK5uy_kfdjh34", repo.extractPlaylistId("RDCLAK5uy_kfdjh34"))
        assertEquals("PL1234567890", repo.extractPlaylistId("  PL1234567890  "))

        // YouTube Music URLs
        val musicUrl = "https://music.youtube.com/playlist?list=RDCLAK5uy_kfdjh34&si=abc123xyz"
        assertEquals("RDCLAK5uy_kfdjh34", repo.extractPlaylistId(musicUrl))

        // Standard YouTube URLs
        val ytUrl = "https://www.youtube.com/playlist?list=PLrAl5_uPj_99"
        assertEquals("PLrAl5_uPj_99", repo.extractPlaylistId(ytUrl))

        // Complex URL with other query params first
        val complexUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLcustomPlaylistId&index=1"
        assertEquals("PLcustomPlaylistId", repo.extractPlaylistId(complexUrl))
    }

    @Test
    fun testImportOnlinePlaylistToLocal() = runBlocking {
        val testDir = tempFolder.newFolder("import_test")
        val customStore = LocalPlaylistStore(storageDir = testDir)

        val onlinePlaylist = DesktopPlaylist(
            id = "RDCLAK5uy_online",
            title = "Top Hits Global",
            author = "YouTube Music",
            description = "Playlist Resmi",
            thumbnailUrl = "https://example.com/cover.jpg",
            songs = listOf(
                DesktopSong("s1", "Song One", "Artist A", "3:15"),
                DesktopSong("s2", "Song Two", "Artist B", "4:20")
            ),
            isLocal = false
        )

        val imported = customStore.createPlaylist(
            title = onlinePlaylist.title,
            description = "Diimpor dari YouTube Music (${onlinePlaylist.author})",
            initialSongs = onlinePlaylist.songs
        )

        assertTrue(imported.id.startsWith("local_"))
        assertEquals("Top Hits Global", imported.title)
        assertEquals(2, imported.songs.size)
        assertTrue(imported.isLocal)

        val allInStore = customStore.getPlaylists()
        assertTrue(allInStore.any { it.id == imported.id })
    }

    @Test
    fun testPlaybackManagerQueueNavigation() {
        val player = VlcAudioPlayer()
        val repo = DesktopMusicRepository()
        val manager = DesktopPlaybackManager(player, repo)

        val songs = listOf(
            DesktopSong("t1", "Track 1", "Artist 1", "3:00"),
            DesktopSong("t2", "Track 2", "Artist 2", "3:30"),
            DesktopSong("t3", "Track 3", "Artist 3", "4:00")
        )

        val testPlaylist = DesktopPlaylist(
            id = "test_pl",
            title = "Test Playlist",
            songs = songs,
            isLocal = true
        )

        // Play playlist starting from index 0
        manager.playPlaylist(testPlaylist, startIndex = 0, shuffle = false)

        assertEquals(3, manager.queue.value.size)
        assertEquals(0, manager.currentIndex.value)
        assertEquals("t1", manager.currentSong.value?.id)
        assertTrue(manager.hasNext)
        assertFalse(manager.hasPrevious)

        // Advance to next track
        manager.playNext()
        assertEquals(1, manager.currentIndex.value)
        assertEquals("t2", manager.currentSong.value?.id)
        assertTrue(manager.hasNext)
        assertTrue(manager.hasPrevious)

        // Advance to last track
        manager.playNext()
        assertEquals(2, manager.currentIndex.value)
        assertEquals("t3", manager.currentSong.value?.id)
        assertFalse(manager.hasNext)
        assertTrue(manager.hasPrevious)

        // Calling playNext at the end with RepeatMode.NONE should stay at 2
        manager.playNext()
        assertEquals(2, manager.currentIndex.value)

        // Enable RepeatMode.ALL
        manager.toggleRepeat()
        assertEquals(RepeatMode.ALL, manager.repeatMode.value)
        assertTrue(manager.hasNext)

        // Now playNext wraps around to 0
        manager.playNext()
        assertEquals(0, manager.currentIndex.value)
        assertEquals("t1", manager.currentSong.value?.id)

        // Previous track navigation with wrap around
        manager.playPrevious()
        assertEquals(2, manager.currentIndex.value)
        assertEquals("t3", manager.currentSong.value?.id)

        // Shuffle toggle
        manager.toggleShuffle()
        assertTrue(manager.isShuffleEnabled.value)
        assertEquals(3, manager.queue.value.size)

        // Unshuffle toggle
        manager.toggleShuffle()
        assertFalse(manager.isShuffleEnabled.value)
        assertEquals("t1", manager.queue.value[0].id)
        assertEquals("t2", manager.queue.value[1].id)
        assertEquals("t3", manager.queue.value[2].id)
    }

    @Test
    fun testShufflePreservesPlaybackHistory() {
        val player = VlcAudioPlayer()
        val repo = DesktopMusicRepository()
        val manager = DesktopPlaybackManager(player, repo)

        val songs = listOf(
            DesktopSong("t1", "Track 1", "Artist 1", "3:00"),
            DesktopSong("t2", "Track 2", "Artist 2", "3:30"),
            DesktopSong("t3", "Track 3", "Artist 3", "4:00"),
            DesktopSong("t4", "Track 4", "Artist 4", "4:30"),
            DesktopSong("t5", "Track 5", "Artist 5", "5:00")
        )

        val playlist = DesktopPlaylist("pl_shuffle_test", "Shuffle Test", songs = songs)
        manager.playPlaylist(playlist, startIndex = 0, shuffle = false)

        // Play t1, then t2, then currently on t3
        manager.playNext() // to t2
        manager.playNext() // to t3
        assertEquals(2, manager.currentIndex.value)
        assertEquals("t3", manager.currentSong.value?.id)

        // Toggle shuffle while on track 3
        manager.toggleShuffle()
        assertTrue(manager.isShuffleEnabled.value)

        val q = manager.queue.value
        assertEquals(5, q.size)
        // Current index should still point to t3
        assertEquals(2, manager.currentIndex.value)
        assertEquals("t3", q[manager.currentIndex.value].id)

        // Past items (indices 0 and 1) MUST be the already-played tracks t1 and t2
        assertEquals(listOf("t1", "t2"), listOf(q[0].id, q[1].id))

        // Upcoming items (indices 3 and 4) must contain ONLY the unplayed tracks t4 and t5
        val upcomingIds = listOf(q[3].id, q[4].id)
        assertTrue(upcomingIds.contains("t4"))
        assertTrue(upcomingIds.contains("t5"))
        assertFalse(upcomingIds.contains("t1"))
        assertFalse(upcomingIds.contains("t2"))

        // Playing next advances to unplayed tracks
        manager.playNext()
        assertEquals(3, manager.currentIndex.value)
        assertTrue(manager.currentSong.value?.id == "t4" || manager.currentSong.value?.id == "t5")

        manager.playNext()
        assertEquals(4, manager.currentIndex.value)
        assertTrue(manager.currentSong.value?.id == "t4" || manager.currentSong.value?.id == "t5")

        // No more tracks to repeat in RepeatMode.NONE
        assertFalse(manager.hasNext)
    }

    @Test
    fun testDirectSongAdditionToExistingPlaylist() = runBlocking {
        val testDir = tempFolder.newFolder("direct_add_test")
        val store = LocalPlaylistStore(storageDir = testDir)

        // Create empty playlist
        val playlist = store.createPlaylist("Akustik Sore", "Koleksi akustik santai")
        assertEquals(0, playlist.songs.size)

        // Add 3 songs directly
        val songA = DesktopSong("a1", "Akustik 1", "Penyanyi A", "3:20", "https://img.com/a.jpg")
        val songB = DesktopSong("b1", "Akustik 2", "Penyanyi B", "4:10", "https://img.com/b.jpg")
        val songC = DesktopSong("c1", "Akustik 3", "Penyanyi C", "2:55", "https://img.com/c.jpg")

        assertTrue(store.addSongToPlaylist(playlist.id, songA))
        assertTrue(store.addSongToPlaylist(playlist.id, songB))
        assertTrue(store.addSongToPlaylist(playlist.id, songC))

        val loaded = store.getPlaylistById(playlist.id)
        assertNotNull(loaded)
        assertEquals(3, loaded!!.songs.size)
        assertEquals("Akustik 1", loaded.songs[0].title)
        assertEquals("Akustik 2", loaded.songs[1].title)
        assertEquals("Akustik 3", loaded.songs[2].title)

        // Ensure thumbnail defaults to the first added song
        assertEquals("https://img.com/a.jpg", loaded.thumbnailUrl)
    }
}
