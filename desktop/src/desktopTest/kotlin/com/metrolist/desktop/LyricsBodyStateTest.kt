package com.metrolist.desktop

import com.metrolist.desktop.model.DesktopSong
import com.metrolist.desktop.ui.LyricsBodyState
import com.metrolist.desktop.ui.resolveLyricsBodyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsBodyStateTest {

    private val songA = DesktopSong(
        id = "song-a",
        title = "Give It To Me",
        artist = "Timbaland",
        durationText = "3:54",
    )

    private val songB = DesktopSong(
        id = "song-b",
        title = "The Way I Are",
        artist = "Timbaland",
        durationText = "2:59",
    )

    @Test
    fun loadingTakesPrecedenceOverLyricsText() {
        assertEquals(
            LyricsBodyState.Loading,
            resolveLyricsBodyState(isLoadingLyrics = true, lyricsText = null, song = songA),
        )
        assertEquals(
            LyricsBodyState.Loading,
            resolveLyricsBodyState(isLoadingLyrics = true, lyricsText = "[00:10.00] Old text", song = songA),
        )
    }

    @Test
    fun nullOrBlankLyricsReturnsEmptyWhenNotLoading() {
        assertEquals(
            LyricsBodyState.Empty,
            resolveLyricsBodyState(isLoadingLyrics = false, lyricsText = null, song = songA),
        )
        assertEquals(
            LyricsBodyState.Empty,
            resolveLyricsBodyState(isLoadingLyrics = false, lyricsText = "   \n\t ", song = songA),
        )
    }

    @Test
    fun nonBlankLyricsReturnsLoadedWithSongAndTextSnapshot() {
        val lrc = "[00:12.34] So give it to me"
        val state = resolveLyricsBodyState(isLoadingLyrics = false, lyricsText = lrc, song = songA)
        assertTrue(state is LyricsBodyState.Loaded)
        val loaded = state as LyricsBodyState.Loaded
        assertEquals(songA, loaded.song)
        assertEquals(lrc, loaded.text)
    }

    @Test
    fun songChangeWhileLoadedPreservesPreviousSnapshotForCrossfadeFadeOut() {
        val songALyrics = "[03:40.00] Wanna see you work your body"
        val previousCrossfadeState = resolveLyricsBodyState(
            isLoadingLyrics = false,
            lyricsText = songALyrics,
            song = songA,
        )

        // When track switches to songB, state resets to loading with null lyricsText
        val nextCrossfadeState = resolveLyricsBodyState(
            isLoadingLyrics = true,
            lyricsText = null,
            song = songB,
        )

        assertEquals(LyricsBodyState.Loading, nextCrossfadeState)
        assertTrue(previousCrossfadeState is LyricsBodyState.Loaded)
        val fadingOutLoaded = previousCrossfadeState as LyricsBodyState.Loaded
        assertEquals(songA, fadingOutLoaded.song)
        assertEquals(songALyrics, fadingOutLoaded.text)
    }
}
