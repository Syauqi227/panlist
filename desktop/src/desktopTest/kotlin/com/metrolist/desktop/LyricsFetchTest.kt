package com.metrolist.desktop

import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.repository.DesktopMusicRepository
import com.metrolist.desktop.ui.LrcParser
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LyricsFetchTest {

    @Test
    fun testRealLyricsFetch() = runBlocking {
        val repo = DesktopMusicRepository()

        val testSongs = listOf(
            DesktopSong(
                id = "7nVctvQVz0U",
                title = "Cardigan",
                artist = "Taylor Swift",
                durationText = "3:59"
            ),
            DesktopSong(
                id = "dummy1",
                title = "Satu Bulan",
                artist = "Bernadya",
                durationText = "3:15"
            ),
            DesktopSong(
                id = "dummy2",
                title = "Gala bunga matahari (Official Lyric Video)",
                artist = "Sal Priadi",
                durationText = "3:50"
            ),
            DesktopSong(
                id = "dummy3",
                title = "Jiwa Yang Bersedih",
                artist = "Ghea Indrawari",
                durationText = "4:38"
            ),
            DesktopSong(
                id = "dummy4",
                title = "Heat Waves",
                artist = "Glass Animals",
                durationText = "3:58"
            ),
            DesktopSong(
                id = "dummy5",
                title = "Stereo Hearts",
                artist = "Gym Class Heroes",
                durationText = "3:34"
            )
        )

        for (song in testSongs) {
            println("==================================================")
            println("Fetching lyrics for: ${song.title} by ${song.artist}")
            val lyrics = repo.getSongLyrics(song)
            if (lyrics == null) {
                println("Result: NULL")
            } else {
                val entries = LrcParser.parse(lyrics)
                println("Result length: ${lyrics.length}, LRC entries: ${entries.size}")
                if (entries.isNotEmpty()) {
                    println("FIRST 3 LRC ENTRIES:")
                    entries.take(3).forEach { println("  [${it.timeMs}ms] ${it.text}") }
                } else {
                    println("FIRST 200 CHARS (PLAIN TEXT):")
                    println(lyrics.take(200))
                }
            }
        }
    }
}
