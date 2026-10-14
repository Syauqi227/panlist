package com.metrolist.desktop

import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.player.DesktopPlaybackManager
import com.metrolist.desktop.player.VlcAudioPlayer
import com.metrolist.desktop.repository.DesktopMusicRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopMusicRepositoryTest {

    @Test
    fun testOfflineDownloadAndPlayback() {
        runBlocking {
            val repository = DesktopMusicRepository()
            val downloadManager = repository.downloadManager

            // Song for testing: 'The Fate of Ophelia' (7nVctvQVz0U)
            val testSong = DesktopSong(
                id = "7nVctvQVz0U",
                title = "The Fate of Ophelia",
                artist = "Taylor Swift",
                durationText = "3:46",
                thumbnailUrl = "https://example.com/cover.jpg"
            )

            println("--- 1. Testing Download Song ---")
            val downloadResult = downloadManager.downloadSong(testSong, repository)
            assertTrue("Download must succeed", downloadResult.isSuccess)
            val downloadedFile = downloadResult.getOrNull()
            assertNotNull(downloadedFile)
            assertTrue("File must exist on disk", downloadedFile!!.exists())
            assertTrue("File size must be greater than 100 KB", downloadedFile.length() > 100 * 1024)
            println("Downloaded file: ${downloadedFile.absolutePath} (${downloadedFile.length()} bytes)")

            println("--- 2. Testing isSongDownloaded and getDownloadedAudioFile ---")
            assertTrue("isSongDownloaded must return true", downloadManager.isSongDownloaded(testSong.id))
            val localAudio = downloadManager.getDownloadedAudioFile(testSong.id)
            assertNotNull("getDownloadedAudioFile must return file", localAudio)
            assertEquals(downloadedFile.absolutePath, localAudio!!.absolutePath)

            println("--- 3. Testing getDownloadedSongs list ---")
            val downloadedList = downloadManager.getDownloadedSongs()
            assertTrue("Downloaded list must not be empty", downloadedList.isNotEmpty())
            assertTrue("Downloaded list must contain test song", downloadedList.any { it.id == testSong.id })

            println("--- 4. Testing Offline Playback via DesktopPlaybackManager ---")
            val player = VlcAudioPlayer()
            val playbackManager = DesktopPlaybackManager(player, repository)
            playbackManager.playSong(testSong)
            assertFalse("isResolving must finish quickly", playbackManager.isResolving.value)
            println("Playback initialized with local file!")
            player.release()

            println("--- 5. Testing Delete Download ---")
            val deleted = downloadManager.deleteDownload(testSong.id)
            assertTrue("Delete must return true", deleted)
            assertFalse("File must no longer be marked downloaded", downloadManager.isSongDownloaded(testSong.id))
            assertFalse("Local file must be removed", downloadedFile.exists())
            println("Offline download test completed successfully!")
        }
    }

    @Test
    fun testNowPlayingSongInfoAndCredits() {
        val repository = DesktopMusicRepository()
        val testSong = DesktopSong(
            id = "test_123",
            title = "Play Hard",
            artist = "Krewella, TrollPhace",
            durationText = "3:30",
            thumbnailUrl = "https://example.com/cover.jpg"
        )

        // 1. Test Credits Generation
        val credits = repository.generateCredits(testSong)
        assertTrue("Credits must not be empty", credits.isNotEmpty())
        val mainArtistCredit = credits.firstOrNull { it.role == "Main Artist" }
        assertNotNull("Must have Main Artist credit", mainArtistCredit)
        assertEquals("Krewella", mainArtistCredit!!.name)
        assertTrue(mainArtistCredit.canFollow)

        val featuredCredit = credits.firstOrNull { it.role == "Featured Artist" }
        assertNotNull("Must have Featured Artist credit", featuredCredit)
        assertEquals("TrollPhace", featuredCredit!!.name)

        assertTrue("Must have Composer credit", credits.any { it.role.contains("Composer") })
        assertTrue("Must have Producer credit", credits.any { it.role.contains("Producer") })

        // 2. Test Tour Dates Generation
        val tours = repository.generateTourDates("Krewella")
        assertTrue("Tour dates must not be empty", tours.isNotEmpty())
        assertEquals(3, tours.size)
        assertEquals("London", tours[0].city)
        assertEquals("9", tours[0].day)
        assertEquals("Okt", tours[0].month)

        // 3. Test Atmospheric Color Extraction
        val color1 = com.metrolist.desktop.ui.NowPlayingColorExtractor.getAtmosphericColor("test_123")
        assertNotNull("Color must not be null", color1)
        val color2 = com.metrolist.desktop.ui.NowPlayingColorExtractor.getAtmosphericColor("test_123")
        assertEquals("Atmospheric color must be deterministic for same song id", color1, color2)
    }
}

